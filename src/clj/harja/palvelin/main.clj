(ns harja.palvelin.main
  (:require

    ;; Yleiset palvelinkomponentit
    [harja.palvelin.komponentit.tietokanta :as tietokanta]
    [harja.palvelin.komponentit.http-palvelin :as http-palvelin]
    [harja.palvelin.komponentit.todennus :as todennus]
    [harja.palvelin.komponentit.fim :as fim]
    [harja.palvelin.komponentit.tapahtumat :as tapahtumat]
    [harja.palvelin.komponentit.sonja :as sonja]

    ;; Integraatiokomponentit
    [harja.palvelin.integraatiot.sampo :as sampo]

    ;; Harjan bisneslogiikkapalvelut
    [harja.palvelin.palvelut.kayttajatiedot :as kayttajatiedot]
    [harja.palvelin.palvelut.urakoitsijat :as urakoitsijat]
    [harja.palvelin.palvelut.haku :as haku]
    [harja.palvelin.palvelut.hallintayksikot :as hallintayksikot]
    [harja.palvelin.palvelut.indeksit :as indeksit]
    [harja.palvelin.palvelut.urakat :as urakat]
    [harja.palvelin.palvelut.urakan-toimenpiteet :as urakan-toimenpiteet]
    [harja.palvelin.palvelut.yksikkohintaiset-tyot :as yksikkohintaiset-tyot]
    [harja.palvelin.palvelut.kokonaishintaiset-tyot :as kokonaishintaiset-tyot]
    [harja.palvelin.palvelut.muut-tyot :as muut-tyot]
    [harja.palvelin.palvelut.toteumat :as toteumat]
    [harja.palvelin.palvelut.toimenpidekoodit :as toimenpidekoodit]
    [harja.palvelin.palvelut.yhteyshenkilot]
    [harja.palvelin.palvelut.paallystys :as paallystys]
    [harja.palvelin.palvelut.kayttajat :as kayttajat]
    [harja.palvelin.palvelut.pohjavesialueet :as pohjavesialueet]
    [harja.palvelin.palvelut.materiaalit :as materiaalit]
    [harja.palvelin.palvelut.selainvirhe :as selainvirhe]
    [harja.palvelin.palvelut.valitavoitteet :as valitavoitteet]
    [harja.palvelin.palvelut.siltatarkastukset :as siltatarkastukset]
    [harja.palvelin.palvelut.lampotilat :as lampotilat]
    [harja.palvelin.palvelut.maksuerat :as maksuerat]
    [harja.palvelin.palvelut.liitteet :as liitteet]
    [harja.palvelin.palvelut.laadunseuranta :as laadunseuranta]

    ;; Harja API
    [harja.palvelin.api.urakat :as api-urakat]
    [harja.palvelin.api.havainnot :as api-havainnot]

    [com.stuartsierra.component :as component]
    [harja.palvelin.asetukset :refer [lue-asetukset konfiguroi-lokitus]]

    [clojure.tools.namespace.repl :refer [refresh]])
  (:gen-class))

(defn luo-jarjestelma [asetukset]
  (let [{:keys [tietokanta http-palvelin kehitysmoodi]} asetukset]
    (konfiguroi-lokitus asetukset)
    (component/system-map
      :db (tietokanta/luo-tietokanta (:palvelin tietokanta)
                                     (:portti tietokanta)
                                     (:tietokanta tietokanta)
                                     (:kayttaja tietokanta)
                                     (:salasana tietokanta))
      :klusterin-tapahtumat (component/using
                              (tapahtumat/luo-tapahtumat)
                              [:db])

      :todennus (component/using
                  (todennus/http-todennus)
                  [:db :klusterin-tapahtumat])
      :http-palvelin (component/using
                       (http-palvelin/luo-http-palvelin (:portti http-palvelin)
                                                        kehitysmoodi)
                       [:todennus])

      ;; Sonja (Sonic ESB) JMS yhteyskomponentti
      :sonja (sonja/luo-sonja (:sonja asetukset))

      ;; FIM REST rajapinta
      :fim (fim/->FIM (:url (:fim asetukset)))

      ;; Sampo rajapinta
      :sampo (component/using (sampo/->Sampo (:lahetysjono-ulos (:sampo asetukset))
                                             (:kuittausjono-ulos (:sampo asetukset))
                                             (:paivittainen-lahetysaika (:sampo asetukset)))
                              [:sonja :db])

      ;; Frontille tarjottavat palvelut
      :kayttajatiedot (component/using
                        (kayttajatiedot/->Kayttajatiedot)
                        [:http-palvelin :db])
      :urakoitsijat (component/using
                      (urakoitsijat/->Urakoitsijat)
                      [:http-palvelin :db])
      :hallintayksikot (component/using
                         (hallintayksikot/->Hallintayksikot)
                         [:http-palvelin :db])
      :haku (component/using
              (haku/->Haku)
              [:http-palvelin :db])
      :indeksit (component/using
                  (indeksit/->Indeksit)
                  [:http-palvelin :db])
      :urakat (component/using
                (urakat/->Urakat)
                [:http-palvelin :db])
      :urakan-toimenpiteet (component/using
                             (urakan-toimenpiteet/->Urakan-toimenpiteet)
                             [:http-palvelin :db])
      :yksikkohintaiset-tyot (component/using
                               (yksikkohintaiset-tyot/->Yksikkohintaiset-tyot)
                               [:http-palvelin :db])
      :kokonaishintaiset-tyot (component/using
                                (kokonaishintaiset-tyot/->Kokonaishintaiset-tyot)
                                [:http-palvelin :db])
      :muut-tyot (component/using
                   (muut-tyot/->Muut-tyot)
                   [:http-palvelin :db])
      :toteumat (component/using
                  (toteumat/->Toteumat)
                  [:http-palvelin :db])
      :paallystys (component/using
                  (paallystys/->Paallystys)
                  [:http-palvelin :db])
      :yhteyshenkilot (component/using
                        (harja.palvelin.palvelut.yhteyshenkilot/->Yhteyshenkilot)
                        [:http-palvelin :db])
      :toimenpidekoodit (component/using
                          (toimenpidekoodit/->Toimenpidekoodit)
                          [:http-palvelin :db])
      :kayttajat (component/using
                   (kayttajat/->Kayttajat)
                   [:http-palvelin :db :fim :klusterin-tapahtumat])
      :pohjavesialueet (component/using
                         (pohjavesialueet/->Pohjavesialueet)
                         [:http-palvelin :db])
      :materiaalit (component/using
                     (materiaalit/->Materiaalit)
                     [:http-palvelin :db])
      :selainvirhe (component/using
                     (selainvirhe/->Selainvirhe)
                     [:http-palvelin])
      :valitavoitteet (component/using
                        (valitavoitteet/->Valitavoitteet)
                        [:http-palvelin :db])
      :siltatarkastukset (component/using
                           (siltatarkastukset/->Siltatarkastukset)
                           [:http-palvelin :db])
      :lampotilat (component/using
                    (lampotilat/->Lampotilat)
                    [:http-palvelin :db])
      :maksuerat (component/using
                   (maksuerat/->Maksuerat)
                   [:http-palvelin :sampo :db])

      :liitteet (component/using
                  (liitteet/->Liitteet)
                  [:http-palvelin :db])

      :laadunseuranta (component/using
                        (laadunseuranta/->Laadunseuranta)
                        [:http-palvelin :db])

      ;; Harja API
      :api-urakat (component/using
                    (api-urakat/->Urakat)
                    [:http-palvelin :db])
      :api-havainnot (component/using
                       (api-havainnot/->Havainnot)
                       [:http-palvelin :db])

      )))

(defonce harja-jarjestelma nil)

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
  (when harja-jarjestelma
    (alter-var-root #'harja-jarjestelma component/stop)))

(defn dev-restart []
  (dev-stop)
  (-main))

(defn dev-refresh []
  (dev-stop)
  (clojure.tools.namespace.repl/set-refresh-dirs "src/clj")
  (refresh :after 'harja.palvelin.main/-main))

(defn dev-julkaise
  "REPL käyttöön: julkaise uusi palvelu (poistaa ensin vanhan samalla nimellä)."
  [nimi fn]
  (http-palvelin/poista-palvelu (:http-palvelin harja-jarjestelma) nimi)
  (http-palvelin/julkaise-palvelu (:http-palvelin harja-jarjestelma) nimi fn))

(defn q
  "Kysele Harjan kannasta, REPL kehitystä varten"
  [& sql]
  (with-open [c (.getConnection (:datasource (:db harja-jarjestelma)))
              ps (.prepareStatement c (reduce str sql))
              rs (.executeQuery ps)]
    (let [cols (-> (.getMetaData rs) .getColumnCount)]
      (loop [res []
             more? (.next rs)]
        (if-not more?
          res
          (recur (conj res (loop [row []
                                  i 1]
                             (if (<= i cols)
                               (recur (conj row (.getObject rs i)) (inc i))
                               row)))
                 (.next rs)))))))

(defn u
  "UPDATE Harjan kantaan"
  [& sql]
  (with-open [c (.getConnection (:datasource (:db harja-jarjestelma)))
              ps (.prepareStatement c (reduce str sql))]
    (.executeUpdate ps)))

