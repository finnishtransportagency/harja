(ns harja.palvelin.palvelut.liitteet
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [cognitect.transit :as t]))


(defn tallenna-liite [req]
  (println "LIITEHÄN SIELTÄ TULI: " req)
  (Thread/sleep 10000)
  {:status 200
   :headers {"Content-Type" "application/transit+json"}
   :body (with-open [out (java.io.ByteArrayOutputStream.)]
           (t/write (t/writer out :json)
                    {:nimi "rekka_kaatui.jpg"
                     :koko 70720
                     :url "/images/rekka_kaatui.jpg"
                     :pikkukuva-url "/images/rekka_kaatui_thumbnail.jpg"})
           (str out))})


(defrecord Liitteet []
  component/Lifecycle
  (start [{:keys [http-palvelin] :as this}]
    (julkaise-palvelu http-palvelin :tallenna-liite
                      tallenna-liite
                      {:ring-kasittelija? true})
    this)

  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin :tallenna-liite)))

  
                        
  
