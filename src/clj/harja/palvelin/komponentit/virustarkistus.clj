(ns harja.palvelin.komponentit.virustarkistus
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.client :as http]
            [taoensso.timbre :as log]))

(def ok-vastaus "Everything ok : true\n")
(def ei-ok-vastaus "Everything ok : false\n")

(defn tarkista [{url :url} nimi sisalto]
  (if-not url
    (do (log/error "Virustarkista ei voi tehdä, ClamAV rest palvelun URL ei asetettu.")
        :error)
    (let [{:keys [status body error]}
          @(http/post url
                      {:timeout 60000
                       :query-params {"name" nimi}
                       :multipart [{:name "file" :content sisalto :filename nimi}]})]
      (cond
        (and (= status 200)
             (= body ok-vastaus))
        :ok

        (and (= status 200)
             (= body ei-ok-vastaus))
        (do
          (log/error "Virus löytynyt! Tiedosto: " nimi)
          (throw (ex-info "Virus havaittu"
                          {:nimi nimi
                           :vastaus body})))

        :default
        (do (log/error "Virustarkistus epäonnistui! " status body url error)
            :error)))))

(defrecord Virustarkistus [url]
  component/Lifecycle
  (start [this]
    this)

  (stop [this]
    this))

(defn luo-virustarkistus [{url :url :as asetukset}]
  (->Virustarkistus url))
