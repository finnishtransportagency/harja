(ns harja.palvelin.main
  (:require
    [taoensso.timbre :as log]
    ;; Yleiset palvelinkomponentit
    [harja.palvelin.komponentit.tietokanta :as tietokanta]
    [harja.palvelin.komponentit.http-palvelin :as http-palvelin]
    [harja.palvelin.komponentit.todennus :as todennus]
    [harja.palvelin.komponentit.fim :as fim]
    [harja.palvelin.komponentit.tapahtumat :as tapahtumat]
    [harja.palvelin.komponentit.sonja :as sonja]
    [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
    [harja.palvelin.komponentit.excel-vienti :as excel-vienti]
    [harja.palvelin.komponentit.virustarkistus :as virustarkistus]
    [harja.palvelin.komponentit.kehitysmoodi :as kehitysmoodi]

    ;; Integraatiokomponentit
    [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
    [harja.palvelin.integraatiot.sampo.sampo-komponentti :as sampo]
    [harja.palvelin.integraatiot.tloik.tloik-komponentti :as tloik]
    [harja.palvelin.integraatiot.tierekisteri.tierekisteri-komponentti :as tierekisteri]
    [harja.palvelin.integraatiot.labyrintti.sms :as labyrintti]
    [harja.palvelin.integraatiot.sonja.sahkoposti :as sonja-sahkoposti]
    [harja.palvelin.integraatiot.sahkoposti :as sahkoposti]
    [harja.palvelin.integraatiot.turi.turi-komponentti :as turi]
    [harja.palvelin.integraatiot.yha.yha-komponentti :as yha-integraatio]

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
    [harja.palvelin.palvelut.yllapito-toteumat :as yllapito-toteumat]
    [harja.palvelin.palvelut.toimenpidekoodit :as toimenpidekoodit]
    [harja.palvelin.palvelut.yhteyshenkilot]
    [harja.palvelin.palvelut.yllapitokohteet.paallystys :as paallystys]
    [harja.palvelin.palvelut.yllapitokohteet.maaramuutokset :as maaramuutokset]
    [harja.palvelin.palvelut.yllapitokohteet.paikkaus :as paikkaus]
    [harja.palvelin.palvelut.yllapitokohteet :as yllapitokohteet]
    [harja.palvelin.palvelut.ping :as ping]
    [harja.palvelin.palvelut.pois-kytketyt-ominaisuudet :as pois-kytketyt-ominaisuudet]
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
    [harja.palvelin.palvelut.laadunseuranta.tarkastukset :as tarkastukset]
    [harja.palvelin.palvelut.yha :as yha]
    [harja.palvelin.palvelut.ilmoitukset :as ilmoitukset]
    [harja.palvelin.palvelut.tietyoilmoitukset :as tietyoilmoitukset]
    [harja.palvelin.palvelut.turvallisuuspoikkeamat :as turvallisuuspoikkeamat]
    [harja.palvelin.palvelut.integraatioloki :as integraatioloki-palvelu]
    [harja.palvelin.palvelut.raportit :as raportit]
    [harja.palvelin.palvelut.tilannekuva :as tilannekuva]
    [harja.palvelin.palvelut.api-jarjestelmatunnukset :as api-jarjestelmatunnukset]
    [harja.palvelin.palvelut.status :as status]
    [harja.palvelin.palvelut.organisaatiot :as organisaatiot]
    [harja.palvelin.palvelut.tienakyma :as tienakyma]
    [harja.palvelin.palvelut.debug :as debug]
    [harja.palvelin.palvelut.hankkeet :as hankkeet]
    [harja.palvelin.palvelut.sopimukset :as sopimukset]
    [harja.palvelin.integraatiot.sahke.sahke-komponentti :as sahke]

    ;; karttakuvien renderöinti
    [harja.palvelin.palvelut.karttakuvat :as karttakuvat]


    ;; Tierekisteriosoitteen selvitys lokaalista tieverkkodatasta
    [harja.palvelin.palvelut.tierek-haku :as tierek-haku]

    ;; Harja API
    [harja.palvelin.integraatiot.api.urakat :as api-urakat]
    [harja.palvelin.integraatiot.api.laatupoikkeamat :as api-laatupoikkeamat]
    [harja.palvelin.integraatiot.api.paivystajatiedot :as api-paivystajatiedot]
    [harja.palvelin.integraatiot.api.pistetoteuma :as api-pistetoteuma]
    [harja.palvelin.integraatiot.api.reittitoteuma :as api-reittitoteuma]
    [harja.palvelin.integraatiot.api.varustetoteuma :as api-varustetoteuma]
    [harja.palvelin.integraatiot.api.siltatarkastukset :as api-siltatarkastukset]
    [harja.palvelin.integraatiot.api.tarkastukset :as api-tarkastukset]
    [harja.palvelin.integraatiot.api.tyokoneenseuranta :as api-tyokoneenseuranta]
    [harja.palvelin.integraatiot.api.turvallisuuspoikkeama :as turvallisuuspoikkeama]
    [harja.palvelin.integraatiot.api.varusteet :as api-varusteet]
    [harja.palvelin.integraatiot.api.ilmoitukset :as api-ilmoitukset]
    [harja.palvelin.integraatiot.api.yllapitokohteet :as api-yllapitokohteet]
    [harja.palvelin.integraatiot.api.ping :as api-ping]
    [harja.palvelin.integraatiot.api.yhteystiedot :as api-yhteystiedot]
    [harja.palvelin.integraatiot.api.tiemerkintatoteuma :as api-tiemerkintatoteuma]

    ;; Ajastetut tehtävät
    [harja.palvelin.ajastetut-tehtavat.paivystystarkistukset :as paivystystarkistukset]
    [harja.palvelin.ajastetut-tehtavat.reittien-validointi :as reittitarkistukset]
    [harja.palvelin.ajastetut-tehtavat.suolasakkojen-lahetys :as suolasakkojen-lahetys]
    [harja.palvelin.ajastetut-tehtavat.geometriapaivitykset :as geometriapaivitykset]
    [harja.palvelin.ajastetut-tehtavat.laskutusyhteenvedot :as laskutusyhteenvedot]
    [harja.palvelin.ajastetut-tehtavat.api-yhteysvarmistus :as api-yhteysvarmistus]
    [harja.palvelin.ajastetut-tehtavat.sonja-jms-yhteysvarmistus :as sonja-jms-yhteysvarmistus]
    [harja.palvelin.ajastetut-tehtavat.tyokoneenseuranta-puhdistus :as tks-putsaus]


    ;; Harja mobiili Laadunseuranta
    [harja-laadunseuranta.core :as harja-laadunseuranta]

    [com.stuartsierra.component :as component]
    [harja.palvelin.asetukset
     :refer [lue-asetukset konfiguroi-lokitus tarkista-asetukset]]

    ;; Metriikat
    [harja.palvelin.komponentit.metriikka :as metriikka])

  (:gen-class))


(defn luo-jarjestelma [asetukset]
  (let [{:keys [tietokanta tietokanta-replica http-palvelin kehitysmoodi]} asetukset]
    (konfiguroi-lokitus asetukset)
    (if-let [virheet (tarkista-asetukset asetukset)]
      (log/error "Validointivirhe asetuksissa:" virheet))

    (component/system-map
      :metriikka (metriikka/luo-jmx-metriikka)
      :db (tietokanta/luo-tietokanta tietokanta kehitysmoodi)
      :db-replica (tietokanta/luo-tietokanta tietokanta-replica kehitysmoodi)
      :klusterin-tapahtumat (component/using
                              (tapahtumat/luo-tapahtumat)
                              [:db])

      :todennus (component/using
                  (todennus/http-todennus (:sahke-headerit asetukset))
                  [:db :klusterin-tapahtumat])
      :http-palvelin (component/using
                       (http-palvelin/luo-http-palvelin http-palvelin
                                                        kehitysmoodi)
                       [:todennus :metriikka])

      :pdf-vienti (component/using
                    (pdf-vienti/luo-pdf-vienti)
                    [:http-palvelin])
      :excel-vienti (component/using
                      (excel-vienti/luo-excel-vienti)
                      [:http-palvelin])

      :virustarkistus (virustarkistus/luo-virustarkistus (:virustarkistus asetukset))

      :liitteiden-hallinta (component/using
                             (harja.palvelin.komponentit.liitteet/->Liitteet)
                             [:db :virustarkistus])

      :kehitysmoodi (component/using
                     (kehitysmoodi/luo-kehitysmoodi kehitysmoodi)
                     [:http-palvelin])

      ;; Integraatioloki
      :integraatioloki
      (component/using (integraatioloki/->Integraatioloki
                         (:paivittainen-lokin-puhdistusaika
                           (:integraatiot asetukset)))
                       [:db])

      ;; Sonja (Sonic ESB) JMS yhteyskomponentti
      :sonja (sonja/luo-sonja (:sonja asetukset))
      :sonja-sahkoposti
      (component/using
        (let [{:keys [vastausosoite jonot suora? palvelin]}
              (:sonja-sahkoposti asetukset)]
          (if suora?
            (sahkoposti/luo-vain-lahetys palvelin vastausosoite)
            (sonja-sahkoposti/luo-sahkoposti vastausosoite jonot)))
        [:sonja :integraatioloki :db])

      ;; FIM REST rajapinta
      :fim (component/using
            (if (and kehitysmoodi (:tiedosto (:fim asetukset)))
               (fim/->FakeFIM (:tiedosto (:fim asetukset)))
               (fim/->FIM (:url (:fim asetukset))))
             [:db :integraatioloki])

      ;; Sampo
      :sampo (component/using (let [sampo (:sampo asetukset)]
                                (sampo/->Sampo (:lahetysjono-sisaan sampo)
                                               (:kuittausjono-sisaan sampo)
                                               (:lahetysjono-ulos sampo)
                                               (:kuittausjono-ulos sampo)
                                               (:paivittainen-lahetysaika sampo)))
                              [:sonja :db :integraatioloki])

      ;; T-LOIK
      :tloik (component/using
               (tloik/->Tloik (:tloik asetukset))
               [:sonja :db :integraatioloki :klusterin-tapahtumat
                :sonja-sahkoposti :labyrintti])

      ;; Tierekisteri
      :tierekisteri (let [asetukset (:tierekisteri asetukset)]
                      (component/using
                        (tierekisteri/->Tierekisteri (:url asetukset)
                                                     (:uudelleenlahetys-aikavali-minuutteina asetukset))
                        [:db :integraatioloki]))

      ;; Labyrintti SMS Gateway
      :labyrintti (component/using
                    (if kehitysmoodi
                      (labyrintti/feikki-labyrintti)
                      (labyrintti/luo-labyrintti (:labyrintti asetukset)))
                    [:http-palvelin :db :integraatioloki])

      :turi (component/using
              (turi/->Turi (:turi asetukset))
              [:db :integraatioloki :liitteiden-hallinta])

      :yha-integraatio (component/using
                         (yha-integraatio/->Yha (:yha asetukset))
                         [:db :integraatioloki])

      :raportointi (component/using
                     (raportointi/luo-raportointi)
                     {:db-replica :db-replica
                      :db :db
                      :pdf-vienti :pdf-vienti
                      :excel-vienti :excel-vienti})

      ;; Tarkastustehtävät

      :paivystystarkistukset (component/using
                               (paivystystarkistukset/->Paivystystarkistukset (:paivystystarkistus asetukset))
                               [:http-palvelin :db :fim :sonja-sahkoposti])
      :reittitarkistukset (component/using
                            (reittitarkistukset/->Reittitarkistukset (:reittitarkistus asetukset))
                            [:http-palvelin :db])

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
      :ping (component/using
              (ping/->Ping)
              [:http-palvelin :db])
      :pois-kytketyt-ominaisuudet (component/using
                                   (pois-kytketyt-ominaisuudet/->PoisKytketytOminaisuudet (:pois-kytketyt-ominaisuudet asetukset))
                                   [:http-palvelin :db])
      :haku (component/using
              (haku/->Haku)
              [:http-palvelin :db])
      :indeksit (component/using
                  (indeksit/->Indeksit)
                  [:http-palvelin :db])
      :urakat (component/using
                (urakat/->Urakat)
                [:http-palvelin :db :sahke])
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
                  [:http-palvelin :db :karttakuvat :tierekisteri])
      :yllapitototeumat (component/using
                          (yllapito-toteumat/->YllapitoToteumat)
                          [:http-palvelin :db])
      :paallystys (component/using
                    (paallystys/->Paallystys)
                    [:http-palvelin :db])
      :maaramuutokset (component/using
                        (maaramuutokset/->Maaramuutokset)
                        [:http-palvelin :db])
      :paikkaus (component/using
                  (paikkaus/->Paikkaus)
                  [:http-palvelin :db])
      :yllapitokohteet (component/using
                         (yllapitokohteet/->Yllapitokohteet)
                         [:http-palvelin :db :fim :sonja-sahkoposti])
      :muokkauslukko (component/using
                       (muokkauslukko/->Muokkauslukko)
                       [:http-palvelin :db])
      :yhteyshenkilot (component/using
                        (harja.palvelin.palvelut.yhteyshenkilot/->Yhteyshenkilot)
                        [:http-palvelin :db :fim])
      :toimenpidekoodit (component/using
                          (toimenpidekoodit/->Toimenpidekoodit)
                          [:http-palvelin :db])
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
                    (lampotilat/->Lampotilat
                      (:lampotilat-url (:ilmatieteenlaitos asetukset)))
                    [:http-palvelin :db])
      :maksuerat (component/using
                   (maksuerat/->Maksuerat)
                   [:http-palvelin :sampo :db])

      :liitteet (component/using
                  (liitteet/->Liitteet)
                  [:http-palvelin :liitteiden-hallinta])

      :laadunseuranta (component/using
                        (laadunseuranta/->Laadunseuranta)
                        [:http-palvelin :db :fim :sonja-sahkoposti :labyrintti])

      :tarkastukset (component/using
                      (tarkastukset/->Tarkastukset)
                      [:http-palvelin :db :karttakuvat])

      :ilmoitukset (component/using
                     (ilmoitukset/->Ilmoitukset)
                     [:http-palvelin :db :tloik])

      :tietyoilmoitukset (component/using
                     (tietyoilmoitukset/->Tietyoilmoitukset)
                     [:http-palvelin :db :pdf-vienti :fim])

      :turvallisuuspoikkeamat (component/using
                                (turvallisuuspoikkeamat/->Turvallisuuspoikkeamat)
                                [:http-palvelin :db :turi])

      :integraatioloki-palvelu (component/using
                                 (integraatioloki-palvelu/->Integraatioloki)
                                 [:http-palvelin :db])
      :raportit (component/using
                  (raportit/->Raportit)
                  [:http-palvelin :db :raportointi :pdf-vienti])

      :yha (component/using
             (yha/->Yha)
             [:http-palvelin :db :yha-integraatio])

      :tr-haku (component/using
                 (tierek-haku/->TierekisteriHaku)
                 [:http-palvelin :db])

      :geometriapaivitykset (component/using
                              (geometriapaivitykset/->Geometriapaivitykset
                                (:geometriapaivitykset asetukset))
                              [:db :integraatioloki])

      :api-yhteysvarmistus (component/using
                             (let [{:keys [ajovali-minuutteina
                                           url
                                           kayttajatunnus
                                           salasana]} (:api-yhteysvarmistus asetukset)]
                               (api-yhteysvarmistus/->ApiVarmistus
                                 ajovali-minuutteina
                                 url
                                 kayttajatunnus
                                 salasana))
                             [:db :integraatioloki])


      :sonja-jms-yhteysvarmistus (component/using
                                   (let [{:keys [ajovali-minuutteina jono]} (:sonja-jms-yhteysvarmistus asetukset)]
                                     (sonja-jms-yhteysvarmistus/->SonjaJmsYhteysvarmistus ajovali-minuutteina jono))
                                   [:db :integraatioloki :sonja :klusterin-tapahtumat])

      :tilannekuva (component/using
                     (tilannekuva/->Tilannekuva)
                     {:db :db-replica
                      :http-palvelin :http-palvelin
                      :karttakuvat :karttakuvat
                      :fim :fim})
      :tienakyma (component/using
                  (tienakyma/->Tienakyma)
                  {:db :db-replica
                   :http-palvelin :http-palvelin})
      :karttakuvat (component/using
                     (karttakuvat/luo-karttakuvat)
                     [:http-palvelin :db])
      :hankkeet (component/using
                   (hankkeet/->Hankkeet)
                   {:db :db-replica
                    :http-palvelin :http-palvelin})
      :sopimukset (component/using
                  (sopimukset/->Sopimukset)
                  {:db :db-replica
                   :http-palvelin :http-palvelin})

      :debug (component/using
              (debug/->Debug)
              {:db :db-replica
               :http-palvelin :http-palvelin})

      :sahke (component/using
               (let [{:keys [lahetysjono uudelleenlahetysaika]} (:sahke asetukset)]
                 (sahke/->Sahke lahetysjono uudelleenlahetysaika))
               [:db :integraatioloki :sonja])
               
      :api-jarjestelmatunnukset (component/using
                                  (api-jarjestelmatunnukset/->APIJarjestelmatunnukset)
                                  [:http-palvelin :db])

      :organisaatiot (component/using
                       (organisaatiot/->Organisaatiot)
                       [:http-palvelin :db])

      ;; Harja API
      :api-urakat (component/using
                    (api-urakat/->Urakat)
                    [:http-palvelin :db :integraatioloki])
      :api-laatupoikkeamat (component/using
                             (api-laatupoikkeamat/->Laatupoikkeamat)
                             [:http-palvelin :db :liitteiden-hallinta
                              :integraatioloki])
      :api-paivystajatiedot (component/using
                              (api-paivystajatiedot/->Paivystajatiedot)
                              [:http-palvelin :db :integraatioloki])
      :api-pistetoteuma (component/using
                          (api-pistetoteuma/->Pistetoteuma)
                          [:http-palvelin :db :integraatioloki])
      :api-reittitoteuma (component/using
                           (api-reittitoteuma/->Reittitoteuma)
                           [:http-palvelin :db :db-replica :integraatioloki])
      :api-varustetoteuma (component/using
                            (api-varustetoteuma/->Varustetoteuma)
                            [:http-palvelin :db :tierekisteri :integraatioloki])
      :api-siltatarkastukset (component/using
                               (api-siltatarkastukset/->Siltatarkastukset)
                               [:http-palvelin :db :integraatioloki])
      :api-tarkastukset (component/using
                          (api-tarkastukset/->Tarkastukset)
                          [:http-palvelin :db :integraatioloki :liitteiden-hallinta])
      :api-tyokoneenseuranta (component/using
                               (api-tyokoneenseuranta/->Tyokoneenseuranta)
                               [:http-palvelin :db])
      :api-tyokoneenseuranta-puhdistus (component/using
                                         (tks-putsaus/->TyokoneenseurantaPuhdistus)
                                         [:db])
      :api-turvallisuuspoikkeama (component/using
                                   (turvallisuuspoikkeama/->Turvallisuuspoikkeama)
                                   [:http-palvelin :db :integraatioloki
                                    :liitteiden-hallinta :turi])
      :api-suolasakkojen-lahetys (component/using
                                   (suolasakkojen-lahetys/->SuolasakkojenLahetys)
                                   [:db])
      :api-varusteet (component/using
                       (api-varusteet/->Varusteet)
                       [:http-palvelin :db :integraatioloki :tierekisteri])
      :api-ilmoitukset (component/using
                         (api-ilmoitukset/->Ilmoitukset)
                         [:http-palvelin :db :integraatioloki :klusterin-tapahtumat
                          :tloik])
      :api-yllapitokohteet (component/using
                             (api-yllapitokohteet/->Yllapitokohteet)
                             [:http-palvelin :db :integraatioloki :liitteiden-hallinta :fim :sonja-sahkoposti])
      :api-ping (component/using
                  (api-ping/->Ping)
                  [:http-palvelin :db :integraatioloki])

      :api-yhteystiedot (component/using
                          (api-yhteystiedot/->Yhteystiedot)
                          [:http-palvelin :db :integraatioloki :fim])

      :api-tiemerkintatoteuma (component/using
                                (api-tiemerkintatoteuma/->Tiemerkintatoteuma)
                                [:http-palvelin :db :integraatioloki])

      ;; Ajastettu laskutusyhteenvetojen muodostus
      :laskutusyhteenvetojen-muodostus
      (component/using
        (laskutusyhteenvedot/->LaskutusyhteenvetojenMuodostus)
        [:db])

      :status (component/using
                (status/luo-status)
                [:http-palvelin :db :db-replica :sonja])

      :mobiili-laadunseuranta
      (component/using
        (harja-laadunseuranta/->Laadunseuranta)
        [:db :http-palvelin]))))

(defonce harja-jarjestelma nil)

(defn kaynnista-jarjestelma [asetusfile lopeta-jos-virhe?]
  (try
    (alter-var-root #'harja-jarjestelma
                    (constantly
                      (-> (lue-asetukset asetusfile)
                          luo-jarjestelma
                          component/start)))
    (status/aseta-status! (:status harja-jarjestelma) 200 "Harja käynnistetty")
    (catch Throwable t
      (log/fatal t "Harjan käynnistyksessä virhe")
      (when lopeta-jos-virhe?
        (System/exit 1)))))

(defn sammuta-jarjestelma []
  (when harja-jarjestelma
    (alter-var-root #'harja-jarjestelma (fn [s]
                                          (component/stop s)
                                          nil))))

(defn -main [& argumentit]
  (kaynnista-jarjestelma (or (first argumentit) "asetukset.edn") true)
  (.addShutdownHook (Runtime/getRuntime) (Thread. sammuta-jarjestelma)))

(defn dev-start []
  (if harja-jarjestelma
    (println "Harja on jo käynnissä!")
    (kaynnista-jarjestelma "asetukset.edn" false)))

(defn dev-stop []
  (sammuta-jarjestelma))

(defn dev-restart []
  (dev-stop)
  (dev-start)
  :ok)


(defn dev-julkaise
  "REPL käyttöön: julkaise uusi palvelu (poistaa ensin vanhan samalla nimellä)."
  [nimi fn]
  (http-palvelin/poista-palvelu (:http-palvelin harja-jarjestelma) nimi)
  (http-palvelin/julkaise-palvelu (:http-palvelin harja-jarjestelma) nimi fn))

(defmacro with-db [s & body]
  `(let [~s (:db harja-jarjestelma)]
     ~@body))

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

(defn explain [sql]
  (q "EXPLAIN (ANALYZE, COSTS, VERBOSE, BUFFERS, FORMAT JSON) " sql))

(defn log-level-info! []
  (log/set-config! [:appenders :standard-out :min-level] :info))

(def figwheel-repl-options
  ;; Nämä ovat Emacsin CIDER ClojureScript repliä varten
  ;; (setq cider-cljs-lein-repl "(do (use 'figwheel-sidecar.repl-api) (start-figwheel! figwheel-repl-options) (cljs-repl))")
  ;; M-x cider-jack-in-clojurescript
  ;;
  {:figwheel-options {}
   :build-ids ["dev"]
   :all-builds
   [{:id "dev"
     :figwheel true
     :source-paths ["src/cljs" "src/cljc" "src/cljs-dev" "test/cljs"]
     :compiler {:optimizations :none :source-map true
                :output-to "dev-resources/js/harja.js"
                :output-dir "dev-resources/js/out"
                :libs ["src/js/kuvataso.js"]
                :closure-output-charset "US-ASCII"}}]})
