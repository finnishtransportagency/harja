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
    [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
    [harja.palvelin.integraatiot.sampo.sampo-komponentti :as sampo]
    [harja.palvelin.integraatiot.tloik.tloik-komponentti :as tloik]

    ;; Raportointi
    [harja.palvelin.raportointi :as raportointi]
    
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
    [harja.palvelin.palvelut.paikkaus :as paikkaus]
    [harja.palvelin.palvelut.kayttajat :as kayttajat]
    [harja.palvelin.palvelut.pohjavesialueet :as pohjavesialueet]
    [harja.palvelin.palvelut.materiaalit :as materiaalit]
    [harja.palvelin.palvelut.selainvirhe :as selainvirhe]
    [harja.palvelin.palvelut.valitavoitteet :as valitavoitteet]
    [harja.palvelin.palvelut.siltatarkastukset :as siltatarkastukset]
    [harja.palvelin.palvelut.lampotilat :as lampotilat]
    [harja.palvelin.palvelut.maksuerat :as maksuerat]
    [harja.palvelin.palvelut.liitteet :as liitteet]
    [harja.palvelin.palvelut.muokkauslukko :as muokkauslukko]
    [harja.palvelin.palvelut.laadunseuranta :as laadunseuranta]
    [harja.palvelin.palvelut.ilmoitukset :as ilmoitukset]
    [harja.palvelin.palvelut.turvallisuuspoikkeamat :as turvallisuuspoikkeamat]
    [harja.palvelin.palvelut.integraatioloki :as integraatioloki-palvelu]
    [harja.palvelin.palvelut.raportit :as raportit]
    [harja.palvelin.palvelut.tyokoneenseuranta :as tyokoneenseuranta]
    
    ;; Harja API
    [harja.palvelin.integraatiot.api.urakat :as api-urakat]
    [harja.palvelin.integraatiot.api.havainnot :as api-havainnot]
    [harja.palvelin.integraatiot.api.paivystajatiedot :as api-paivystajatiedot]
    [harja.palvelin.integraatiot.api.pistetoteuma :as api-pistetoteuma]
    [harja.palvelin.integraatiot.api.reittitoteuma :as api-reittitoteuma]
    [harja.palvelin.integraatiot.api.tarkastukset :as api-tarkastukset]
    [harja.palvelin.integraatiot.api.tyokoneenseuranta :as api-tyokoneenseuranta]
    [harja.palvelin.integraatiot.api.tyokoneenseuranta-puhdistus :as tks-putsaus]
    [harja.palvelin.integraatiot.api.turvallisuuspoikkeama :as turvallisuuspoikkeama]

    ;; Ajastetut tehtävät
    [harja.palvelin.ajastetut-tehtavat.suolasakkojen-lahetys :as suolasakkojen-lahetys]

    [com.stuartsierra.component :as component]
    [harja.palvelin.asetukset :refer [lue-asetukset konfiguroi-lokitus]])
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

      :liitteiden-hallinta (component/using
                             (harja.palvelin.komponentit.liitteet/->Liitteet)
                             [:db])

      ;; Integraatioloki
      :integraatioloki (component/using (integraatioloki/->Integraatioloki
                                          (:paivittainen-lokin-puhdistusaika (:integraatiot asetukset)))
                                        [:db])

      ;; Sonja (Sonic ESB) JMS yhteyskomponentti
      :sonja (sonja/luo-sonja (:sonja asetukset))

      ;; FIM REST rajapinta
      :fim (fim/->FIM (:url (:fim asetukset)))

      ;; Sampo
      :sampo (component/using (sampo/->Sampo (:lahetysjono-sisaan (:sampo asetukset))
                                             (:kuittausjono-sisaan (:sampo asetukset))
                                             (:lahetysjono-ulos (:sampo asetukset))
                                             (:kuittausjono-ulos (:sampo asetukset))
                                             (:paivittainen-lahetysaika (:sampo asetukset)))
                              [:sonja :db :integraatioloki])

      ;; T-LOIK
      :tloik (component/using (tloik/->Tloik (:ilmoitusviestijono (:tloik asetukset))
                                             (:ilmoituskuittausjono (:tloik asetukset)))
                              [:sonja :db :integraatioloki])

      :raportointi (component/using
                    (raportointi/luo-raportointi)
                    [:db])
      
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
      :muokkauslukko (component/using
                    (muokkauslukko/->Muokkauslukko)
                    [:http-palvelin :db])
      :paikkaus (component/using
                    (paikkaus/->Paikkaus)
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
                    (lampotilat/->Lampotilat (:lampotilat-url (:ilmatieteenlaitos asetukset)))
                    [:http-palvelin :db])
      :maksuerat (component/using
                   (maksuerat/->Maksuerat)
                   [:http-palvelin :sampo :db])

      :liitteet (component/using
                  (liitteet/->Liitteet)
                  [:http-palvelin :liitteiden-hallinta])

      :laadunseuranta (component/using
                        (laadunseuranta/->Laadunseuranta)
                        [:http-palvelin :db])

      :ilmoitukset (component/using
                     (ilmoitukset/->Ilmoitukset)
                     [:http-palvelin :db])

      :turvallisuuspoikkeamat (component/using
                                (turvallisuuspoikkeamat/->Turvallisuuspoikkeamat)
                                [:http-palvelin :db])

      :integraatioloki-palvelu (component/using
                                (integraatioloki-palvelu/->Integraatioloki)
                                [:http-palvelin :db])
      :raportit (component/using
                 (raportit/->Raportit)
                 [:http-palvelin :db :raportointi])

      :tyokoneenseuranta (component/using
                          (tyokoneenseuranta/->TyokoneseurantaHaku)
                          [:http-palvelin :db])
      
      ;; Harja API
      :api-urakat (component/using
                    (api-urakat/->Urakat)
                    [:http-palvelin :db :integraatioloki])
      :api-havainnot (component/using
                       (api-havainnot/->Havainnot)
                       [:http-palvelin :db :liitteiden-hallinta :integraatioloki])
      :api-paivystajatiedot (component/using
                          (api-paivystajatiedot/->Paivystajatiedot)
                          [:http-palvelin :db :integraatioloki])
      :api-pistetoteuma (component/using
                          (api-pistetoteuma/->Pistetoteuma)
                          [:http-palvelin :db :integraatioloki])
      :api-reittitoteuma (component/using
                          (api-reittitoteuma/->Reittitoteuma)
                          [:http-palvelin :db :integraatioloki])
      :api-tarkastukset (component/using
                          (api-tarkastukset/->Tarkastukset)
                          [:http-palvelin :db :integraatioloki :liitteiden-hallinta])
      :api-tyokoneenseuranta (component/using
                              (api-tyokoneenseuranta/->Tyokoneenseuranta)
                              [:http-palvelin :db])
      :api-tyokoneenseuranta-puhdistus (component/using (tks-putsaus/->TyokoneenseurantaPuhdistus)
                                                        [:db])
      :api-turvallisuuspoikkeama (component/using (turvallisuuspoikkeama/->Turvallisuuspoikkeama)
                                                        [:http-palvelin :db :integraatioloki :liitteiden-hallinta])
      :api-suolasakkojen-lahetys (component/using (suolasakkojen-lahetys/->SuolasakkojenLahetys)
                                                        [:db])
      )))

(defonce harja-jarjestelma nil)

(defn kaynnista-jarjestelma [asetusfile]
  (alter-var-root #'harja-jarjestelma
                  (constantly
                   (-> (lue-asetukset asetusfile)
                       luo-jarjestelma
                       component/start))))

(defn sammuta-jarjestelma []
  (when harja-jarjestelma
    (alter-var-root #'harja-jarjestelma (fn [s]
                                          (component/stop s)
                                          nil))))

(defn -main [& argumentit]
  (kaynnista-jarjestelma (or (first argumentit) "asetukset.edn"))
  (.addShutdownHook (Runtime/getRuntime) (Thread. sammuta-jarjestelma)))

(defn dev-start []
  (if harja-jarjestelma
    (println "Harja on jo käynnissä!")
    (kaynnista-jarjestelma "asetukset.edn")))

(defn dev-stop []
  (sammuta-jarjestelma))

(defn dev-restart []
  (dev-stop)
  (dev-start))


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

