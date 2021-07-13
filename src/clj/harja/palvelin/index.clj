(ns harja.palvelin.index
  (:require [hiccup.core :refer [html]]
            [clojure.java.io :as io]
            [taoensso.timbre :as log])
  (:import [javax.crypto Mac]
           [javax.crypto.spec SecretKeySpec]
           [java.util Base64]
           (hiccup.compiler HtmlRenderer)))

(def anti-forgery-fallback-key "d387gcsb8137hd9h192hdijsha9hd91hdiubisab98f7g7812g8dfheiqufhsaiud8713")

(defn- anti-forgery-seed
  "Palauttaa joko argumenttina annetun avaimen (joka on luettu asetuksista)
   tai fallback avaimen error-logituksen kera."
  [avain]
  (if (string? avain)
    avain
    (do
      (log/error "Käytetään ei-turvallista fallback-avainta anti-CSRF-tokenin generoimiseen!")
      anti-forgery-fallback-key)))

(defn muodosta-csrf-token
  "Käyttää random avainta ja anti-csrf-token-secret-keytä anti-CSRF-tokenin generoimiseen."
  [random-string anti-csrf-token-secret-key]
  (let [secret-key (SecretKeySpec. (.getBytes
                                     (anti-forgery-seed anti-csrf-token-secret-key)
                                     "UTF-8")
                                   "HmacSHA256")
        mac (Mac/getInstance "HmacSHA256")]
    (.init mac secret-key)
    (String. (.encode (Base64/getEncoder) (.doFinal mac (.getBytes random-string "UTF-8"))))))

(defn tee-random-avain []
  (with-open [in (io/input-stream (io/file "/dev/urandom"))]
    (let [buf (byte-array 16)
          n (.read in buf)]
      (assert (= n 16))
      (String. (.encode (Base64/getEncoder) buf)))))

(defn tee-paasivu [random-avain devmode]
  (html
    "<!DOCTYPE html>\n"
    [:html
     [:head
      [:title "HARJA"]
      [:meta {:http-equiv "X-UA-Compatible" :content "IE=edge,chrome=1"}]
      ;; Edge yrittää muuttaa isot numerot puhelinnumerolinkeiksi. Ei haluta sitä käytöstä.
      [:meta {:name "format-detection" :content "telephone=no"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"}]
      [:meta {:name "apple-mobile-web-app-capable" :content "yes"}]
      [:meta {:name "mobile-web-app-capable" :content "yes"}]
      [:meta {:charset "utf-8"}]
      [:link {:href "//fonts.googleapis.com/css?family=Open+Sans:400,600,700" :rel "stylesheet" :type "text/css"}]
      [:link {:href "css/application.css" :rel "stylesheet" :type "text/css"}]
      [:link {:rel "icon" :type "image/png" :href "images/harja_favicon.png"}]]
     [:body {:data-anti-csrf-token random-avain}
      [:div#app
       [:div {:style "display: flex;"}
        [:div {:style "flex: 1; display: flex; justify-content: center; align-items: center; height: 100vh;"}
         [:img {:src "images/ajax-loader.gif"}]]]]
      [:script {:type "text/javascript" :src "js/harja.js"}]]]))

(defn tee-ls-paasivu [random-avain devmode]
  (let [livicons-osoite (if devmode "resources/public/laadunseuranta/img/"
                                    "public/laadunseuranta/img/")
        livicons-18 (if devmode
                      (slurp (str livicons-osoite "livicons-18.svg"))
                      (slurp (io/resource (str livicons-osoite "livicons-18.svg"))))
        livicons-24 (if devmode
                      (slurp (str livicons-osoite "livicons-24.svg"))
                      (slurp (io/resource (str livicons-osoite "livicons-24.svg"))))
        livicons-36 (if devmode
                      (slurp (str livicons-osoite "livicons-36.svg"))
                      (slurp (io/resource (str livicons-osoite "livicons-36.svg"))))
        inline-svg-18 (reify HtmlRenderer
                        (render-html [_]
                          livicons-18))
        inline-svg-24 (reify HtmlRenderer
                        (render-html [_]
                          livicons-24))
        inline-svg-36 (reify HtmlRenderer
                        (render-html [_]
                          livicons-36))]
    (html
      "<!DOCTYPE html>\n"
      [:html
       [:head
        [:title "HARJA Mobiili laadunseuranta"]
        [:meta {:charset "utf-8"}]
        [:meta {:name "viewport" :content "width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"}]
        [:meta {:name "apple-mobile-web-app-capable" :content "yes"}]
        [:meta {:name "mobile-web-app-capable" :content "yes"}]
        [:link {:href "//fonts.googleapis.com/css?family=Open+Sans:400,700" :rel "stylesheet" :type "text/css"}]
        [:link {:href "../css/laadunseuranta.css" :rel "stylesheet" :type "text/css"}]
        [:link {:rel "icon" :type "image/png" :href "images/harja_favicon.png"}]
        [:script {:type "text/javascript" :src "js/json3.min.js"}]
        [:script {:type "text/javascript" :src "js/proj4.js"}]
        [:script {:type "text/javascript"}
         "proj4.defs(\"urn:x-ogc:def:crs:EPSG:3067\", \"+proj=utm +zone=35 +ellps=GRS80 +units=m +no_defs\");\n
          proj4.defs(\"EPSG:3067\", proj4.defs(\"urn:x-ogc:def:crs:EPSG:3067\"));"]]
       [:body {:data-anti-csrf-token random-avain}
        [:div {:style "display: none;"}
         inline-svg-18 inline-svg-24 inline-svg-36]
        [:video {:preload "true" :id "keep-alive-hack" :loop "true"}
         [:source {:src "video/keep_alive.mp4" :type "video/mp4"}]
         [:source {:src "video/keep_alive.webm" :type "video/webm"}]
         [:source {:src "video/keep_alive.ogv" :type "video/ogv"}]]
        [:div#app]
        [:script {:type "text/javascript" :src "js/compiled/harja_laadunseuranta.js"}]]])))
