(ns harja.palvelin.index
  (:use [hiccup.core]
        [ring.middleware.anti-forgery]))

(defn tee-paasivu [devmode]
  (html
   (if devmode
     [:html
      [:head
       [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
       [:link {:href "//fonts.googleapis.com/css?family=Open+Sans:400,700" :rel "stylesheet" :type "text/css"}]
       [:link {:rel "stylesheet/less" :type "text/css" :href "less/application/application.less"}]
       [:link {:rel "icon" :type "image/png" :href "/images/harja_favicon.png"}]
       [:script {:type "text/javascript" :src "js/less-2.5.0.js"}]
       [:script {:type "text/javascript" :src "js/proj4.js"}]
       [:script {:type "text/javascript"}
        "proj4.defs(\"urn:x-ogc:def:crs:EPSG:3067\", \"+proj=utm +zone=35 +ellps=GRS80 +units=m +no_defs\");"
        "proj4.defs(\"EPSG:3067\", proj4.defs(\"urn:x-ogc:def:crs:EPSG:3067\"));"
        "window.anti_csrf_token=\"" *anti-forgery-token* "\";"]]
      [:body {:onload "harja.asiakas.main.harja()"}
       [:div#anti-csrf-token {:style "display: none;"} *anti-forgery-token*]
       [:div#app]
       [:script {:src "js/out/goog/base.js" :type "text/javascript"}]
       [:script {:src "js/harja.js" :type "text/javascript"}]
       [:script {:type "text/javascript"}
        "goog.require(\"harja.asiakas.main\");"]]]

     [:html
      [:head
       [:meta {:http-equiv "X-UA-Compatible" :content "IE=edge,chrome=1"}]
       [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
       [:link {:href "//fonts.googleapis.com/css?family=Open+Sans:400,700" :rel "stylesheet" :type "text/css"}]
       [:link {:href "css/application.css" :rel "stylesheet" :type "text/css"}]
       [:link {:rel "icon" :type "image/png" :href "/images/harja_favicon.png"}]
       [:script {:type "text/javascript" :src "js/harja.js"}]
       [:script {:type "text/javascript" :src "js/proj4.js"}]
       [:script {:type "text/javascript"}
        "proj4.defs(\"urn:x-ogc:def:crs:EPSG:3067\", \"+proj=utm +zone=35 +ellps=GRS80 +units=m +no_defs\");"
        "proj4.defs(\"EPSG:3067\", proj4.defs(\"urn:x-ogc:def:crs:EPSG:3067\"));"
        "window.anti_csrf_token=\"" *anti-forgery-token* "\";"]]
      [:body {:onload "harja.asiakas.main.harja()"}
       [:div#anti-csrf-token {:style "display: none;"} *anti-forgery-token*]
       [:div#app]]])))
