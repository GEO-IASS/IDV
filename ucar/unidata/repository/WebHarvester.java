/**
 * $Id: TrackDataSource.java,v 1.90 2007/08/06 17:02:27 jeffmc Exp $
 *
 * Copyright 1997-2005 Unidata Program Center/University Corporation for
 * Atmospheric Research, P.O. Box 3000, Boulder, CO 80307,
 * support@unidata.ucar.edu.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or (at
 * your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */

package ucar.unidata.repository;


import org.w3c.dom.*;


import ucar.unidata.sql.SqlUtil;
import ucar.unidata.util.DateUtil;
import ucar.unidata.util.HtmlUtil;
import ucar.unidata.util.HttpServer;
import ucar.unidata.util.IOUtil;
import ucar.unidata.util.LogUtil;
import ucar.unidata.util.Misc;

import ucar.unidata.util.StringBufferCollection;
import ucar.unidata.util.StringUtil;
import ucar.unidata.util.TwoFacedObject;
import ucar.unidata.xml.XmlUtil;

import java.io.File;
import java.io.InputStream;

import java.lang.reflect.*;



import java.net.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;


import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;



import java.util.regex.*;


/**
 * Class SqlUtil _more_
 *
 *
 * @author IDV Development Team
 * @version $Revision: 1.3 $
 */
public class WebHarvester extends Harvester {

    /** _more_          */
    public static final String TAG_URLS = "urls";

    /** _more_          */
    public static final String ATTR_URLS = "urls";


    /** _more_ */
    private List<String> patternNames = new ArrayList<String>();


    /** _more_          */
    private String urls = "";

    /** _more_ */
    User user;


    /** _more_          */
    List<String> statusMessages = new ArrayList<String>();

    /** _more_ */
    private int entryCnt = 0;

    /** _more_ */
    private int newEntryCnt = 0;


    /**
     * _more_
     *
     * @param repository _more_
     * @param id _more_
     *
     * @throws Exception _more_
     */
    public WebHarvester(Repository repository, String id) throws Exception {
        super(repository, id);
    }

    /**
     * _more_
     *
     * @param repository _more_
     * @param element _more_
     *
     * @throws Exception _more_
     */
    public WebHarvester(Repository repository, Element element)
            throws Exception {
        super(repository, element);
    }




    /**
     * _more_
     *
     * @param element _more_
     *
     * @throws Exception _more_
     */
    protected void init(Element element) throws Exception {
        super.init(element);
        rootDir = new File(XmlUtil.getAttribute(element, ATTR_ROOTDIR, ""));
        urls    = XmlUtil.getGrandChildText(element, TAG_URLS);
        if (urls == null) {
            urls = "";
        }
    }

    /**
     * _more_
     *
     * @return _more_
     *
     * @throws Exception _more_
     */
    protected User getUser() throws Exception {
        if (user == null) {
            user = repository.getUserManager().getDefaultUser();
        }
        return user;
    }

    /**
     * _more_
     *
     * @param element _more_
     *
     * @throws Exception _more_
     */
    public void applyState(Element element) throws Exception {
        super.applyState(element);
        XmlUtil.create(element.getOwnerDocument(), TAG_URLS, element, urls,
                       null);
    }



    /**
     * _more_
     *
     * @param request _more_
     *
     * @throws Exception _more_
     */
    public void applyEditForm(Request request) throws Exception {
        super.applyEditForm(request);
        urls = request.getUnsafeString(ATTR_URLS, "").trim();
    }



    /**
     * _more_
     *
     * @param request _more_
     * @param sb _more_
     *
     * @throws Exception _more_
     */
    public void createEditForm(Request request, StringBuffer sb)
            throws Exception {
        super.createEditForm(request, sb);
        sb.append(HtmlUtil.formEntry(msgLabel("Fetch URLS"),
                                     HtmlUtil.textArea(ATTR_URLS, urls, 5,
                                         40)));
        sb.append(
            RepositoryManager.tableSubHeader("Then create an entry with"));

        sb.append(HtmlUtil.formEntry(msgLabel("Name template"),
                                     HtmlUtil.input(ATTR_NAMETEMPLATE,
                                         nameTemplate, HtmlUtil.SIZE_60)));
        sb.append(HtmlUtil.formEntry(msgLabel("Description template"),
                                     HtmlUtil.input(ATTR_DESCTEMPLATE,
                                         descTemplate, HtmlUtil.SIZE_60)));

        sb.append(HtmlUtil.formEntry(msgLabel("Group template"),
                                     HtmlUtil.input(ATTR_GROUPTEMPLATE,
                                         groupTemplate, HtmlUtil.SIZE_60)));
    }




    /**
     * _more_
     *
     * @return _more_
     *
     * @throws Exception _more_
     */
    public String getExtraInfo() throws Exception {
        String error = getError();
        if (error != null) {
            return super.getExtraInfo();
        }

        return status.toString() + StringUtil.join("", statusMessages);
    }




    /**
     * _more_
     *
     * @throws Exception _more_
     */
    protected void runInner() throws Exception {
        if ( !getActive()) {
            return;
        }
        entryCnt       = 0;
        newEntryCnt    = 0;
        statusMessages = new ArrayList<String>();
        status         = new StringBuffer("Fetching URLS<br>");
        int cnt = 0;
        while (getActive()) {
            long t1 = System.currentTimeMillis();
            collectEntries((cnt == 0));
            long t2 = System.currentTimeMillis();
            cnt++;
            if ( !getMonitor()) {
                status = new StringBuffer("Done<br>");
                break;
            }

            status.append("Done... sleeping for " + getSleepMinutes()
                          + " minutes<br>");
            Misc.sleep((long) (getSleepMinutes() * 60 * 1000));
            status = new StringBuffer();
        }
    }




    /**
     * _more_
     *
     * @param firstTime _more_
     *
     *
     * @throws Exception _more_
     */
    public void collectEntries(boolean firstTime) throws Exception {

        List<Entry> entries = new ArrayList<Entry>();
        for (String url : (List<String>) StringUtil.split(urls, "\n", true,
                true)) {
            if ( !getActive()) {
                return;
            }
            Entry entry = processUrl(url);
            if (entry != null) {
                entries.add(entry);
                if (statusMessages.size() > 100) {
                    statusMessages = new ArrayList<String>();
                }
                statusMessages.add(repository.getBreadCrumbs(null, entry,
                        true, "", null)[1]);

                entryCnt++;
            }
        }

        newEntryCnt += entries.size();
        repository.insertEntries(entries, true, true);
    }



    /**
     * _more_
     *
     * @param f _more_
     *
     * @param url _more_
     *
     * @return _more_
     *
     * @throws Exception _more_
     */
    private Entry processUrl(String url) throws Exception {
        String fileName = url;
        String tail     = IOUtil.getFileTail(url);
        File   tmpFile  = getStorageManager().getTmpFile(null, tail);
        //        System.err.println ("fetching");
        IOUtil.writeTo(new URL(url), tmpFile, null);
        File newFile = getStorageManager().moveToStorage(null, tmpFile,
                           getRepository().getGUID() + "_");

        //        System.err.println ("got it " + newFile);
        String tag        = tagTemplate;
        String groupName  = groupTemplate;
        String name       = nameTemplate;
        String desc       = descTemplate;


        Date   createDate = new Date();
        Date   fromDate   = createDate;
        Date   toDate     = createDate;
        String ext        = IOUtil.getFileExtension(url);
        tag = tag.replace("${extension}", ext);

        //        groupName = groupName.replace("${dirgroup}", dirGroup);

        groupName = groupName.replace("${fromdate}",
                                      getRepository().formatDate(fromDate));
        groupName = groupName.replace("${todate}",
                                      getRepository().formatDate(toDate));

        name = name.replace("${filename}", tail);
        name = name.replace("${fromdate}",
                            getRepository().formatDate(fromDate));

        name = name.replace("${todate}", getRepository().formatDate(toDate));

        desc = desc.replace("${fromdate}",
                            getRepository().formatDate(fromDate));
        desc = desc.replace("${todate}", getRepository().formatDate(toDate));
        desc = desc.replace("${name}", name);


        Group group = repository.findGroupFromName(groupName, getUser(),
                          true);
        Entry entry = typeHandler.createEntry(repository.getGUID());
        Resource resource = new Resource(newFile.toString(),
                                         Resource.TYPE_STOREDFILE);

        entry.initEntry(name, desc, group, group.getCollectionGroupId(),
                        getUser(), resource, "", createDate.getTime(),
                        fromDate.getTime(), toDate.getTime(), null);
        if (tag.length() > 0) {
            List tags = StringUtil.split(tag, ",", true, true);
            for (int i = 0; i < tags.size(); i++) {
                entry.addMetadata(new Metadata(repository.getGUID(),
                        entry.getId(), EnumeratedMetadataHandler.TYPE_TAG,
                        DFLT_INHERITED, (String) tags.get(i), "", "", ""));
            }

        }
        typeHandler.initializeNewEntry(entry);
        return entry;

    }




}

