(ns harja.palvelin.main
  (:require

   ;; Yleiset palvelinkomponenti
   [harja.palvelin.komponentit.tietokanta :as tietokanta]
   [harja.palvelin.komponentit.http-palvelin :as http-palvelin]
   [harja.palvelin.komponentit.todennus :as todennus]
   
   ;; Harjan bisneslogiikkapalvelut
   [harja.palvelin.palvelut.kayttajatiedot :as kayttajatiedot]
   [harja.palvelin.palvelut.urakoitsijat :as urakoitsijat]
   [harja.palvelin.palvelut.hallintayksikot :as hallintayksikot]
   [harja.palvelin.palvelut.urakat :as urakat]
   [harja.palvelin.palvelut.toimenpidekoodit :as toimenpidekoodit]
   [harja.palvelin.palvelut.yhteyshenkilot]
   
   [com.stuartsierra.component :as component]
   [harja.palvelin.asetukset :refer [lue-asetukset konfiguroi-lokitus]])
  (:gen-class))

(defn luo-jarjestelma [asetukset]
  (let [{:keys [tietokanta http-palvelin kehitysmoodi]} asetukset]
    (component/system-map
     :db (tietokanta/luo-tietokanta (:palvelin tietokanta)
                                    (:portti tietokanta)
                                    (:tietokanta tietokanta)
                                    (:kayttaja tietokanta)
                                    (:salasana tietokanta))
     :todennus (if kehitysmoodi
                 (todennus/feikki-http-todennus {:etunimi "Tero" :sukunimi "Toripolliisi" :id 1 :kayttajanimi "LX123456789"})
                 (todennus/http-todennus))
     :http-palvelin (component/using
                     (http-palvelin/luo-http-palvelin (:portti http-palvelin)
                                                      kehitysmoodi)
                     [:todennus])

     ;; Frontille tarjottavat palvelut 
     :kayttajatiedot (component/using
                      (kayttajatiedot/->Kayttajatiedot)
                      [:http-palvelin])
     :urakoitsijat (component/using
                       (urakoitsijat/->Urakoitsijat)
                       [:http-palvelin :db])
     :hallintayksikot (component/using
                       (hallintayksikot/->Hallintayksikot)
                       [:http-palvelin :db])
     :urakat (component/using
              (urakat/->Urakat)
              [:http-palvelin :db])
     :yhteyshenkilot (component/using
                      (harja.palvelin.palvelut.yhteyshenkilot/->Yhteyshenkilot)
                      [:http-palvelin :db])
     
     :toimenpidekoodit (component/using
                        (toimenpidekoodit/->Toimenpidekoodit)
                        [:http-palvelin :db])
     )))

(def harja-jarjestelma nil)

(defn -main [& argumentit]
  (alter-var-root #'harja-jarjestelma
                  (constantly
                   (-> (lue-asetukset (or (first argumentit) "asetukset.edn"))
                       luo-jarjestelma
                       component/start)))
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. (fn []
                               (component/stop harja-jarjestelma)))))

(defn dev-start []
  (alter-var-root #'harja-jarjestelma component/start))

(defn dev-stop []
  (alter-var-root #'harja-jarjestelma component/stop))

(defn dev-restart []
  (dev-stop)
  (-main))

(defn dev-julkaise
  "REPL käyttöön: julkaise uusi palvelu (poistaa ensin vanhan samalla nimellä)."
  [nimi fn]
  (http-palvelin/poista-palvelu (:http-palvelin harja-jarjestelma) nimi)
  (http-palvelin/julkaise-palvelu (:http-palvelin harja-jarjestelma) nimi fn))
