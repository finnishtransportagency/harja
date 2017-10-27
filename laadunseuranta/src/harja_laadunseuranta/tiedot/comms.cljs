(ns harja-laadunseuranta.tiedot.comms
  (:require [ajax.core :refer [POST GET raw-response-format]]
            [cljs.core.async :as async :refer [put! <! chan close!]]
            [harja-laadunseuranta.tiedot.sovellus :as s]
            [harja-laadunseuranta.tiedot.asetukset.asetukset :as asetukset])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn- hanskaa-virhe [response c]
  (let [{:keys [failure status status-text]} response]
    #_(when-not (or (= :abort failure)
                  (= :failure failure))
      (set! (.-location js/window) (str (.-location js/window) "?relogin=true")))
    (js/console.log (str "Virhe: " failure status status-text response))
    (close! c)))

(defn post! [url data]
  (let [c (chan)]
    (POST url {:params        data
               :error-handler #(hanskaa-virhe % c)
               :handler       #(do
                                (if (and (map? %) (contains? % :error))
                                  (do
                                    (reset! s/palvelinvirhe (pr-str %))
                                    (.warn js/console (str "Virhe: " (pr-str %))))
                                  (do
                                    (reset! s/palvelinvirhe nil)
                                    (put! c %)))
                                (close! c))
               :format        :transit})
    c))

(defn send-file! [url file-data mime-type]
  (let [c (chan)
        decoded (js/window.atob file-data)
        a (js/Uint8Array. (.-length decoded))
        _ (doseq [i (range (.-length decoded))]
            (aset a i (.charCodeAt decoded i)))
        blobi (js/Blob. (clj->js [a]) #js {:type mime-type})
        form-data (doto (js/FormData.)
                    (.append "liite" blobi "filename.jpg"))]
    (POST url {:body form-data
               :response-format (raw-response-format)
               :error-handler #(hanskaa-virhe % c)
               :handler #(put! c (js/parseInt %))})
    c))

(defn get! [url]
  (let [c (chan)]
    (GET url {:error-handler #(close! c)
              :handler #(put! c %)
              :format :transit})
    c))

(defn hae-urakkatyypin-urakat [urakkatyyppi]
  (post! asetukset/+urakkatyypin-urakat-url+ urakkatyyppi))

(defn paata-ajo! [tarkastusajo-id urakka]
  (post! asetukset/+paatos-url+ {:urakka urakka
                                 :tarkastusajo {:id tarkastusajo-id}}))

(defn luo-ajo! []
  (post! asetukset/+luonti-url+ nil))

(defn hae-simuloitu-tarkastusajo! [tarkastusajo-id]
  (post! asetukset/+simuloitu-ajo-url+ {:tarkastusajo-id tarkastusajo-id}))

(defn hae-kayttajatiedot [sijainti]
  (post! asetukset/+kayttajatiedot-url+ {:sijainti sijainti}))

(defn- tallenna-kuvat
  "Tallentaa lähetettävien reittimerkintöjen kuvat palvelimelle ja korvaa kuvadatan kuvan id:llä"
  [reittimerkinnat]
  (go-loop [merkinnat reittimerkinnat
            result []]
    (if-let [eka (first merkinnat)]
      (if (get eka "kuva")
        (when-let [kuvaid (<! (send-file! asetukset/+liitteen-tallennus-url+
                                          (get-in eka ["kuva" "data"])
                                          (get-in eka ["kuva" "mime-type"])))]
          (recur (rest merkinnat) (conj result (assoc eka "kuva" kuvaid))))
        (recur (rest merkinnat) (conj result eka)))
      result)))

(defn laheta-reittimerkinnat!
  "Lähettää joukon reittimerkintöjä,
   palauttaa kanavan josta voi lukea vektorina lähetettyjen viestien id:t"
  [reittimerkinnat]
  (go
    (if-not (empty? reittimerkinnat)
      (do
        ;; tallenna kaikki kuvat ensin, tulee nil jos ei onnistunut
        (if-let [reittimerkinnat (<! (tallenna-kuvat reittimerkinnat))]
          (if (<! (post! asetukset/+tallennus-url+ {:kirjaukset reittimerkinnat}))
            (let [poistetut (mapv #(get % "id") reittimerkinnat)]
              poistetut)
            [])
          []))
      [])))

(defn hae-tr-tiedot [sijainti]
  (post! asetukset/+tr-tietojen-haku-url+ (assoc (select-keys sijainti [:lat :lon])
                                                 :treshold asetukset/+tros-haun-treshold+)))

(defn hae-tiedosto [url]
  (let [c (chan)]
    (GET url {:handler #(put! c %)
              :format :raw})
    c))
