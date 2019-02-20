package de.pixart.messenger.utils;

import android.content.Context;
import android.os.AsyncTask;
import android.view.View;
import android.webkit.URLUtil;

import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URI;
import java.net.URISyntaxException;

import de.pixart.messenger.Config;

/**
 * Created by ponna on 16-01-2018.
 */

public class RichPreview {

    private static final String RICH_LINK_METADATA = "richlink_meta_data";
    private MetaData metaData;
    private ResponseListener responseListener;
    private String url;
    private String filename;
    private Context context;

    public RichPreview(ResponseListener responseListener) {
        this.responseListener = responseListener;
        metaData = new MetaData();
    }

    public void getPreview(final String url, final String filename, final Context context) {
        this.url = url;
        this.filename = filename;
        this.context = context;
        new getData().execute();
    }

    private class getData extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            FileInputStream fis = null;
            ObjectInputStream is = null;
            final File file = new File(context.getCacheDir(), RICH_LINK_METADATA + "/" + filename);
            try {
                fis = new FileInputStream(file);
                InputStreamReader isr = new InputStreamReader(fis);
                BufferedReader bufferedReader = new BufferedReader(isr);
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    sb.append(line);
                }
                String string = sb.substring(sb.indexOf("{"), sb.lastIndexOf("}") + 1);
                JSONObject json = new JSONObject(string);
                if (json.has("url")) {
                    metaData.setUrl(json.getString("url"));
                }
                if (json.has("imageurl")) {
                    metaData.setImageurl(json.getString("imageurl"));
                }
                if (json.has("title")) {
                    metaData.setTitle(json.getString("title"));
                }
                if (json.has("description")) {
                    metaData.setDescription(json.getString("description"));
                }
                if (json.has("sitename")) {
                    metaData.setSitename(json.getString("sitename"));
                }
                if (json.has("mediatype")) {
                    metaData.setMediatype(json.getString("mediatype"));
                }
                if (json.has("favicon")) {
                    metaData.setFavicon(json.getString("favicon"));
                }
            } catch (Exception e) {
                retrieveMeta(url, context);
                e.printStackTrace();
            } finally {
                try {
                    if (fis != null) {
                        fis.close();
                    }
                    if (is != null) {
                        is.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            responseListener.onData(metaData);
        }
    }

    private String resolveURL(String url, String part) {
        if (URLUtil.isValidUrl(part)) {
            return part;
        } else {
            URI base_uri = null;
            try {
                base_uri = new URI(url);
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
            base_uri = base_uri != null ? base_uri.resolve(part) : null;
            return base_uri != null ? base_uri.toString() : null;
        }
    }

    private void saveMeta(MetaData metaData, Context context) {
        final File file = new File(context.getCacheDir(), RICH_LINK_METADATA + "/" + filename);
        file.getParentFile().mkdirs();
        FileOutputStream fos = null;
        ObjectOutputStream oos = null;
        boolean keep = true;
        try {
            fos = new FileOutputStream(file);
            oos = new ObjectOutputStream(fos);
            JSONObject json = new JSONObject();
            json.put("url", metaData.getUrl());
            json.put("imageurl", metaData.getImageurl());
            json.put("title", metaData.getTitle());
            json.put("description", metaData.getDescription());
            json.put("sitename", metaData.getSitename());
            json.put("mediatype", metaData.getMediatype());
            json.put("favicon", metaData.getFavicon());
            oos.writeObject(json.toString());
        } catch (Exception e) {
            e.printStackTrace();
            keep = false;
        } finally {
            try {
                if (oos != null) oos.close();
                if (fos != null) fos.close();
                if (!keep) file.delete();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void retrieveMeta(String url, Context context) {
        Document doc = null;
        try {
            doc = Jsoup.connect(url)
                    .timeout(Config.CONNECT_TIMEOUT * 1000)
                    .get();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Elements elements = null;
        if (doc != null) {
            elements = doc.getElementsByTag("meta");

            // getTitle doc.select("meta[property=og:title]")
            String title = doc.select("meta[property=og:title]").attr("content");

            if (title == null || title.isEmpty()) {
                title = doc.title();
            }
            metaData.setTitle(title);

            //getDescription
            String description = doc.select("meta[name=description]").attr("content");
            if (description == null || description.isEmpty()) {
                description = doc.select("meta[name=Description]").attr("content");
            }
            if (description == null || description.isEmpty()) {
                description = doc.select("meta[property=og:description]").attr("content");
            }
            if (description == null || description.isEmpty()) {
                description = "";
            }
            metaData.setDescription(description);


            // getMediaType
            Elements mediaTypes = doc.select("meta[name=medium]");
            String type = "";
            if (mediaTypes.size() > 0) {
                String media = mediaTypes.attr("content");

                type = media.equals("image") ? "photo" : media;
            } else {
                type = doc.select("meta[property=og:type]").attr("content");
            }
            metaData.setMediatype(type);


            //getImages
            Elements imageElements = doc.select("meta[property=og:image]");
            if (imageElements.size() > 0) {
                String image = imageElements.attr("content");
                if (!image.isEmpty()) {
                    metaData.setImageurl(resolveURL(url, image));
                }
            }
            if (metaData.getImageurl().isEmpty()) {
                String src = doc.select("link[rel=image_src]").attr("href");
                if (!src.isEmpty()) {
                    metaData.setImageurl(resolveURL(url, src));
                } else {
                    src = doc.select("link[rel=apple-touch-icon]").attr("href");
                    if (!src.isEmpty()) {
                        metaData.setImageurl(resolveURL(url, src));
                        metaData.setFavicon(resolveURL(url, src));
                    } else {
                        src = doc.select("link[rel=icon]").attr("href");
                        if (!src.isEmpty()) {
                            metaData.setImageurl(resolveURL(url, src));
                            metaData.setFavicon(resolveURL(url, src));
                        }
                    }
                }
            }

            //Favicon
            String src = doc.select("link[rel=apple-touch-icon]").attr("href");
            if (!src.isEmpty()) {
                metaData.setFavicon(resolveURL(url, src));
            } else {
                src = doc.select("link[rel=icon]").attr("href");
                if (!src.isEmpty()) {
                    metaData.setFavicon(resolveURL(url, src));
                }
            }
        }

        if (elements != null) {
            for (Element element : elements) {
                if (element.hasAttr("property")) {
                    String str_property = element.attr("property").trim();
                    if (str_property.equals("og:url")) {
                        metaData.setUrl(element.attr("content"));
                    }
                    if (str_property.equals("og:site_name")) {
                        metaData.setSitename(element.attr("content"));
                    }
                }
            }
        }

        if (metaData.getUrl().equals("") || metaData.getUrl().isEmpty()) {
            URI uri = null;
            try {
                uri = new URI(url);
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
            if (url == null) {
                metaData.setUrl(url);
            } else {
                metaData.setUrl(uri != null ? uri.getHost() : null);
            }
        }
        saveMeta(metaData, context);
    }

    public interface ResponseListener {

        void onData(MetaData metaData);

        void onError(Exception e);
    }

    public interface RichLinkListener {

        void onClicked(View view, MetaData meta);
    }

    public interface ViewListener {

        void onSuccess(boolean status);

        void onError(Exception e);
    }
}