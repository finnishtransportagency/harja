(ns harja-laadunseuranta.comms
  (:require [ajax.core :refer [POST GET raw-response-format]]
            [cljs.core.async :as async :refer [put! <! chan close!]]
            [harja-laadunseuranta.asetukset :as asetukset])
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
    (POST url {:params data
               :error-handler #(hanskaa-virhe % c)
               :handler #(put! c %)
               :format :transit})
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
  (post! asetukset/+paatos-url+ {:urakka (:id urakka)
                                 :tarkastusajo {:id tarkastusajo-id}}))

(defn luo-ajo! [tarkastustyyppi]
  (post! asetukset/+luonti-url+ {:tyyppi tarkastustyyppi}))

(defn hae-kayttajatiedot []
  (get! asetukset/+kayttajatiedot-url+))

(defn- tallenna-kuvat
  "Tallentaa lähetettävien tapahtumien kuvat palvelimelle ja korvaa kuvadatan kuvan id:llä"
  [tapahtumat]
  (go-loop [tp tapahtumat
            result []]
    (if-let [t (first tp)]
      (if (get t "kuva")
        (when-let [kuvaid (<! (send-file! asetukset/+liitteen-tallennus-url+
                                          (get-in t ["kuva" "data"])
                                          (get-in t ["kuva" "mime-type"])))]
          (recur (rest tp) (conj result (assoc t "kuva" kuvaid))))
        (recur (rest tp) (conj result t)))
      result)))

(defn laheta-tapahtumat!
  "Lähettää joukon tapahtumia, palauttaa kanavan josta voi lukea vektorina lähetettyjen viestien id:t"
  [tapahtumat]
  (go
    (if-not (empty? tapahtumat)
      (do
        ;; tallenna kaikki kuvat ensin, tulee nil jos ei onnistunut
        (if-let [tapahtumat (<! (tallenna-kuvat tapahtumat))]
          (do #_(js/console.log (str "Lähetetään tapahtumat: " (pr-str tapahtumat)))
              (if (<! (post! asetukset/+tallennus-url+ {:kirjaukset tapahtumat}))
                (let [poistetut (mapv #(get % "id") tapahtumat)]
                  #_(js/console.log (str "Poistetut id:t " (pr-str poistetut)))
                  poistetut)
                []))
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
