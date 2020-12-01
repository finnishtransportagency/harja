(ns harja.palvelin.main
  (:require
    [taoensso.timbre :as log]
    [clojure.core.async :refer [<! go timeout]]
    [harja.palvelin.tyokalut.jarjestelma :as jarjestelma]
    [harja.palvelin.tyokalut.tapahtuma-tulkkaus :as tapahtumien-tulkkaus]
    [tarkkailija.palvelin.tarkkailija :as tarkkailija]
    ;; Yleiset palvelinkomponentit
    [harja.palvelin.komponentit.tietokanta :as tietokanta]
    [harja.palvelin.komponentit.http-palvelin :as http-palvelin]
    [harja.palvelin.komponentit.todennus :as todennus]
    [harja.palvelin.komponentit.fim :as fim]
    [harja.palvelin.komponentit.sonja :as sonja]
    [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
    [harja.palvelin.komponentit.excel-vienti :as excel-vienti]
    [harja.palvelin.komponentit.virustarkistus :as virustarkistus]
    [harja.palvelin.komponentit.tiedostopesula :as tiedostopesula]
    [harja.palvelin.komponentit.kehitysmoodi :as kehitysmoodi]
    [harja.palvelin.komponentit.komponenttien-tila :as komponenttien-tila]
    [harja.palvelin.komponentit.liitteet :as liitteet-komp]

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
    [harja.palvelin.integraatiot.yha.yha-paikkauskomponentti :as yha-paikkauskomponentti]

    [harja.palvelin.integraatiot.velho.velho-komponentti :as velho-integraatio]
    [harja.palvelin.integraatiot.sahke.sahke-komponentti :as sahke]
    [harja.palvelin.integraatiot.vkm.vkm-komponentti :as vkm]
    [harja.palvelin.integraatiot.reimari.reimari-komponentti :as reimari]
    [harja.palvelin.integraatiot.digitraffic.ais-data :as ais-data]

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
    [harja.palvelin.palvelut.budjettisuunnittelu :as budjettisuunnittelu]
    [harja.palvelin.palvelut.yksikkohintaiset-tyot :as yksikkohintaiset-tyot]
    [harja.palvelin.palvelut.kokonaishintaiset-tyot :as kokonaishintaiset-tyot]
    [harja.palvelin.palvelut.muut-tyot :as muut-tyot]
    [harja.palvelin.palvelut.tehtavamaarat :as tehtavamaarat]
    [harja.palvelin.palvelut.laskut :as laskut]
    [harja.palvelin.palvelut.aliurakoitsijat :as aliurakoitsijat]
    [harja.palvelin.palvelut.toteumat :as toteumat]
    [harja.palvelin.palvelut.yllapito-toteumat :as yllapito-toteumat]
    [harja.palvelin.palvelut.toimenpidekoodit :as toimenpidekoodit]
    [harja.palvelin.palvelut.yhteyshenkilot]
    [harja.palvelin.palvelut.yllapitokohteet.paallystys :as paallystys]
    [harja.palvelin.palvelut.yllapitokohteet.pot2 :as pot2]
    [harja.palvelin.palvelut.yllapitokohteet.maaramuutokset :as maaramuutokset]
    [harja.palvelin.palvelut.yllapitokohteet.paikkaukset :as paikkaukset]
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
    [harja.palvelin.palvelut.velho :as velho]
    [harja.palvelin.palvelut.ilmoitukset :as ilmoitukset]
    [harja.palvelin.palvelut.tietyoilmoitukset :as tietyoilmoitukset]
    [harja.palvelin.palvelut.turvallisuuspoikkeamat :as turvallisuuspoikkeamat]
    [harja.palvelin.palvelut.integraatioloki :as integraatioloki-palvelu]
    [harja.palvelin.palvelut.raportit :as raportit]
    [harja.palvelin.palvelut.tilannekuva :as tilannekuva]
    [harja.palvelin.palvelut.api-jarjestelmatunnukset :as api-jarjestelmatunnukset]
    [harja.palvelin.palvelut.geometria-aineistot :as geometria-aineistot]
    [harja.palvelin.palvelut.status :as status]
    [harja.palvelin.palvelut.organisaatiot :as organisaatiot]
    [harja.palvelin.palvelut.tienakyma :as tienakyma]
    [harja.palvelin.palvelut.debug :as debug]
    [harja.palvelin.palvelut.hankkeet :as hankkeet]
    [harja.palvelin.palvelut.sopimukset :as sopimukset]
    [harja.palvelin.palvelut.urakan-tyotunnit :as urakan-tyotunnit]
    [harja.palvelin.palvelut.hairioilmoitukset :as hairioilmoitukset]
    [harja.palvelin.palvelut.jarjestelman-tila :as jarjestelman-tila]

    ;; karttakuvien renderöinti
    [harja.palvelin.palvelut.karttakuvat :as karttakuvat]


    ;; Tierekisteriosoitteen selvitys lokaalista tieverkkodatasta
    [harja.palvelin.palvelut.tierekisteri-haku :as tierekisteri-haku]

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
    [harja.palvelin.integraatiot.api.urakan-tyotunnit :as api-urakan-tyotunnit]
    [harja.palvelin.integraatiot.api.tieluvat :as api-tieluvat]
    [harja.palvelin.integraatiot.api.paikkaukset :as api-paikkaukset]

    [harja.palvelin.palvelut.tieluvat :as tieluvat]

    ;; Ajastetut tehtävät
    [harja.palvelin.ajastetut-tehtavat.paivystystarkistukset :as paivystystarkistukset]
    [harja.palvelin.ajastetut-tehtavat.reittien-validointi :as reittitarkistukset]
    [harja.palvelin.ajastetut-tehtavat.suolasakkojen-lahetys :as suolasakkojen-lahetys]
    [harja.palvelin.ajastetut-tehtavat.geometriapaivitykset :as geometriapaivitykset]
    [harja.palvelin.ajastetut-tehtavat.laskutusyhteenvedot :as laskutusyhteenvedot]
    [harja.palvelin.ajastetut-tehtavat.api-yhteysvarmistus :as api-yhteysvarmistus]
    [harja.palvelin.ajastetut-tehtavat.sonja-jms-yhteysvarmistus :as sonja-jms-yhteysvarmistus]
    [harja.palvelin.ajastetut-tehtavat.tyokoneenseuranta-puhdistus :as tks-putsaus]
    [harja.palvelin.ajastetut-tehtavat.vaylien-geometriat :as vaylien-geometriat]
    [harja.palvelin.ajastetut-tehtavat.kanavasiltojen-geometriat :as kanavasiltojen-geometriat]
    [harja.palvelin.ajastetut-tehtavat.urakan-tyotuntimuistutukset :as urakan-tyotuntimuistutukset]
    [harja.palvelin.tyokalut.koordinaatit :as koordinaatit]


    ;; Harja mobiili Laadunseuranta
    [harja-laadunseuranta.core :as harja-laadunseuranta]

    [com.stuartsierra.component :as component]
    [harja.palvelin.asetukset
     :refer [lue-asetukset konfiguroi-lokitus tarkista-asetukset tarkista-ymparisto!]]

    ;; Metriikat
    [harja.palvelin.komponentit.metriikka :as metriikka]

    ;; Vesiväylät
    [harja.palvelin.palvelut.vesivaylat.toimenpiteet :as vv-toimenpiteet]
    [harja.palvelin.palvelut.vesivaylat.vaylat :as vv-vaylat]
    [harja.palvelin.palvelut.vesivaylat.hinnoittelut :as vv-hinnoittelut]
    [harja.palvelin.palvelut.vesivaylat.kiintiot :as vv-kiintiot]
    [harja.palvelin.palvelut.vesivaylat.materiaalit :as vv-materiaalit]
    [harja.palvelin.palvelut.vesivaylat.turvalaitteet :as vv-turvalaitteet]
    [harja.palvelin.palvelut.vesivaylat.alukset :as vv-alukset]

    ;; Kanavat
    [harja.palvelin.palvelut.kanavat.kohteet :as kan-kohteet]

    [harja.palvelin.palvelut.kanavat.liikennetapahtumat :as kan-liikennetapahtumat]
    [harja.palvelin.palvelut.kanavat.hairiotilanteet :as kan-hairio]
    [harja.palvelin.palvelut.kanavat.kanavatoimenpiteet :as kan-toimenpiteet]

    [harja.palvelin.tyokalut.tapahtuma-apurit :as event-apurit])

  (:gen-class))

(def asetukset-tiedosto "asetukset.edn")


(defn luo-jarjestelma [asetukset]
  (let [{:keys [tietokanta tietokanta-replica http-palvelin kehitysmoodi]} asetukset]
    (component/system-map
      :metriikka (metriikka/luo-jmx-metriikka)
      :db (tietokanta/luo-tietokanta (assoc tietokanta
                                       :tarkkailun-timeout-arvot
                                       (select-keys (get-in asetukset [:komponenttien-tila :db])
                                                    #{:paivitystiheys-ms :kyselyn-timeout-ms})
                                       :tarkkailun-nimi :db)
                                     kehitysmoodi)
      :db-replica (tietokanta/luo-tietokanta (assoc tietokanta-replica
                                               :tarkkailun-timeout-arvot
                                               (select-keys (get-in asetukset [:komponenttien-tila :db-replica])
                                                            #{:paivitystiheys-ms :replikoinnin-max-viive-ms})
                                               :tarkkailun-nimi :db-replica)
                                             kehitysmoodi)

      :todennus (component/using
                  (todennus/http-todennus (:sahke-headerit asetukset))
                  [:db])
      :http-palvelin (component/using
                       (http-palvelin/luo-http-palvelin http-palvelin
                                                        kehitysmoodi)
                       [:todennus :metriikka :db])

      :pdf-vienti (component/using
                    (pdf-vienti/luo-pdf-vienti)
                    [:http-palvelin])
      :excel-vienti (component/using
                      (excel-vienti/luo-excel-vienti)
                      [:http-palvelin])

      :virustarkistus (virustarkistus/luo-virustarkistus (:virustarkistus asetukset))

      :tiedostopesula (tiedostopesula/luo-tiedostopesula (:tiedostopesula asetukset))

      :liitteiden-hallinta (component/using
                             (liitteet-komp/->Liitteet
                               (get-in asetukset [:liitteet :fileyard-url]))
                             [:db :virustarkistus :tiedostopesula :pois-kytketyt-ominaisuudet])

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
      :sonja (component/using
               (sonja/luo-sonja (merge (:sonja asetukset)
                                       (select-keys (get-in asetukset [:komponenttien-tila :sonja])
                                                    #{:paivitystiheys-ms})))
               [:db])
      :sonja-sahkoposti
      (component/using
        (let [{:keys [vastausosoite jonot suora? palvelin]}
              (:sonja-sahkoposti asetukset)]
          (if suora?
            (sahkoposti/luo-vain-lahetys palvelin vastausosoite)
            (sonja-sahkoposti/luo-sahkoposti vastausosoite jonot)))
        [:sonja :integraatioloki :db])

      :solita-sahkoposti
      (component/using
        (let [{:keys [vastausosoite palvelin]} (:solita-sahkoposti asetukset)]
          (sahkoposti/luo-vain-lahetys palvelin vastausosoite))
        [:integraatioloki :db])

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
                              [:sonja :db :integraatioloki :pois-kytketyt-ominaisuudet])

      ;; T-LOIK
      :tloik (component/using
               (tloik/->Tloik (:tloik asetukset) (:kehitysmoodi asetukset))
               [:sonja :db :integraatioloki
                :sonja-sahkoposti :labyrintti])

      ;; Tierekisteri
      :tierekisteri (let [asetukset (:tierekisteri asetukset)]
                      (component/using
                        (tierekisteri/->Tierekisteri (:url asetukset)
                                                     (:uudelleenlahetys-aikavali-minuutteina asetukset))
                        [:db :integraatioloki :pois-kytketyt-ominaisuudet]))

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

      :yha-paikkauskomponentti (component/using
                         (yha-paikkauskomponentti/->YhaPaikkaukset (:yha asetukset))
                         [:db :integraatioloki])

      :velho-integraatio (component/using
                           (velho-integraatio/->Velho (:velho asetukset))
                           [:db :integraatioloki])

      :raportointi (component/using
                     (raportointi/luo-raportointi)
                     {:db-replica :db-replica
                      :db :db
                      :pdf-vienti :pdf-vienti
                      :excel-vienti :excel-vienti})

      :komponenttien-tila (komponenttien-tila/komponentin-tila (:komponenttien-tila asetukset))

      ;; Tarkastustehtävät

      :paivystystarkistukset (component/using
                               (paivystystarkistukset/->Paivystystarkistukset (:paivystystarkistus asetukset))
                               [:http-palvelin :db :fim :sonja-sahkoposti])
      :reittitarkistukset (component/using
                            (reittitarkistukset/->Reittitarkistukset (:reittitarkistus asetukset))
                            [:http-palvelin :db :pois-kytketyt-ominaisuudet])

      ;; Frontille tarjottavat palvelut
      :kayttajatiedot (component/using
                        (kayttajatiedot/->Kayttajatiedot)
                        [:http-palvelin :db :solita-sahkoposti])
      :urakoitsijat (component/using
                      (urakoitsijat/->Urakoitsijat)
                      [:http-palvelin :db :pois-kytketyt-ominaisuudet])
      :hallintayksikot (component/using
                         (hallintayksikot/->Hallintayksikot)
                         [:http-palvelin :db :pois-kytketyt-ominaisuudet])
      :ping (component/using
              (ping/->Ping)
              [:http-palvelin :db])
      :pois-kytketyt-ominaisuudet (component/using
                                    (pois-kytketyt-ominaisuudet/->PoisKytketytOminaisuudet (:pois-kytketyt-ominaisuudet asetukset))
                                    [:http-palvelin])
      :haku (component/using
              (haku/->Haku)
              [:http-palvelin :db :pois-kytketyt-ominaisuudet])
      :indeksit (component/using
                  (indeksit/->Indeksit)
                  [:http-palvelin :db :pois-kytketyt-ominaisuudet])
      :urakat (component/using
                (urakat/->Urakat)
                [:http-palvelin :db :sahke :pois-kytketyt-ominaisuudet])
      :urakan-toimenpiteet (component/using
                             (urakan-toimenpiteet/->Urakan-toimenpiteet)
                             [:http-palvelin :db :pois-kytketyt-ominaisuudet
                              :pois-kytketyt-ominaisuudet])
      :budjettisuunnittelu (component/using
                               (budjettisuunnittelu/->Budjettisuunnittelu)
                               [:http-palvelin :db :pois-kytketyt-ominaisuudet])
      :yksikkohintaiset-tyot (component/using
                               (yksikkohintaiset-tyot/->Yksikkohintaiset-tyot)
                               [:http-palvelin :db :pois-kytketyt-ominaisuudet])
      :kokonaishintaiset-tyot (component/using
                                (kokonaishintaiset-tyot/->Kokonaishintaiset-tyot)
                                [:http-palvelin :db :pois-kytketyt-ominaisuudet])
      :muut-tyot (component/using
                   (muut-tyot/->Muut-tyot)
                   [:http-palvelin :db :pois-kytketyt-ominaisuudet])
      :tehtavamaarat (component/using
                   (tehtavamaarat/->Tehtavamaarat)
                   [:http-palvelin :db :pois-kytketyt-ominaisuudet])
      :laskut (component/using
                (laskut/->Laskut)
                [:http-palvelin :db :pdf-vienti :excel-vienti :pois-kytketyt-ominaisuudet])
      :aliurakoitsijat (component/using
                (aliurakoitsijat/->Aliurakoitsijat)
                [:http-palvelin :db :pois-kytketyt-ominaisuudet])
      :toteumat (component/using
                  (toteumat/->Toteumat)
                  [:http-palvelin :db :db-replica :pois-kytketyt-ominaisuudet :karttakuvat :tierekisteri])
      :vv-toimenpiteet (component/using
                         (vv-toimenpiteet/->Toimenpiteet)
                         [:http-palvelin :db :pois-kytketyt-ominaisuudet])
      :vv-vaylat (component/using
                   (vv-vaylat/->Vaylat)
                   [:http-palvelin :db :pois-kytketyt-ominaisuudet])
      :vv-hinnoittelut (component/using
                         (vv-hinnoittelut/->Hinnoittelut)
                         [:http-palvelin :db :pois-kytketyt-ominaisuudet])
      :vv-kiintiot (component/using
                     (vv-kiintiot/->Kiintiot)
                     [:http-palvelin :db :pois-kytketyt-ominaisuudet])
      :vv-materiaalit (component/using
                        (vv-materiaalit/->Materiaalit)
                        [:http-palvelin :db :pois-kytketyt-ominaisuudet :fim :sonja-sahkoposti])
      :vv-turvalaitteet (component/using
                          (vv-turvalaitteet/->Turvalaitteet)
                          [:http-palvelin :db :pois-kytketyt-ominaisuudet])
      :vv-alukset (component/using
                    (vv-alukset/->Alukset)
                    [:http-palvelin :db :pois-kytketyt-ominaisuudet])
      :kan-kohteet (component/using
                     (kan-kohteet/->Kohteet)
                     [:http-palvelin :db :pois-kytketyt-ominaisuudet])
      :kan-liikennetapahtumat (component/using
                                (kan-liikennetapahtumat/->Liikennetapahtumat)
                                [:http-palvelin :db :pois-kytketyt-ominaisuudet])
      :kan-hairio (component/using
                    (kan-hairio/->Hairiotilanteet)
                    [:http-palvelin :db :pois-kytketyt-ominaisuudet :fim :sonja-sahkoposti])
      :kan-toimenpiteet (component/using
                          (kan-toimenpiteet/->Kanavatoimenpiteet)
                          [:http-palvelin :db :pois-kytketyt-ominaisuudet :fim :sonja-sahkoposti])
      :yllapitototeumat (component/using
                          (yllapito-toteumat/->YllapitoToteumat)
                          [:http-palvelin :db :pois-kytketyt-ominaisuudet])
      :paallystys (component/using
                    (paallystys/->Paallystys)
                    [:http-palvelin :db :pois-kytketyt-ominaisuudet :fim :sonja-sahkoposti])
      :pot2 (component/using
                    (pot2/->POT2)
                    [:http-palvelin :db :pois-kytketyt-ominaisuudet :fim :sonja-sahkoposti])
      :maaramuutokset (component/using
                        (maaramuutokset/->Maaramuutokset)
                        [:http-palvelin :db :pois-kytketyt-ominaisuudet])
      :paikkaukset (component/using
                     (paikkaukset/->Paikkaukset)
                     [:http-palvelin :db :fim :sonja-sahkoposti :yha-paikkauskomponentti])
      :yllapitokohteet (component/using
                         (let [asetukset (:yllapitokohteet asetukset)]
                           (yllapitokohteet/->Yllapitokohteet asetukset))
                         [:http-palvelin :db :yha-integraatio :pois-kytketyt-ominaisuudet :fim :sonja-sahkoposti :vkm])
      :muokkauslukko (component/using
                       (muokkauslukko/->Muokkauslukko)
                       [:http-palvelin :db :pois-kytketyt-ominaisuudet])
      :yhteyshenkilot (component/using
                        (harja.palvelin.palvelut.yhteyshenkilot/->Yhteyshenkilot)
                        [:http-palvelin :db :pois-kytketyt-ominaisuudet :fim])
      :toimenpidekoodit (component/using
                          (toimenpidekoodit/->Toimenpidekoodit)
                          [:http-palvelin :db :pois-kytketyt-ominaisuudet])
      :pohjavesialueet (component/using
                         (pohjavesialueet/->Pohjavesialueet)
                         [:http-palvelin :db :pois-kytketyt-ominaisuudet])
      :materiaalit (component/using
                     (materiaalit/->Materiaalit)
                     [:http-palvelin :db :pois-kytketyt-ominaisuudet])
      :selainvirhe (component/using
                     (selainvirhe/->Selainvirhe kehitysmoodi)
                     [:http-palvelin])
      :valitavoitteet (component/using
                        (valitavoitteet/->Valitavoitteet)
                        [:http-palvelin :db :pois-kytketyt-ominaisuudet])
      :siltatarkastukset (component/using
                           (siltatarkastukset/->Siltatarkastukset)
                           [:http-palvelin :db :pois-kytketyt-ominaisuudet])
      :lampotilat (component/using
                    (lampotilat/->Lampotilat
                      (:lampotilat-url (:ilmatieteenlaitos asetukset)))
                    [:http-palvelin :db :pois-kytketyt-ominaisuudet])
      :maksuerat (component/using
                   (maksuerat/->Maksuerat)
                   [:http-palvelin :sampo :db :pois-kytketyt-ominaisuudet])

      :liitteet (component/using
                  (liitteet/->Liitteet)
                  [:http-palvelin :db :liitteiden-hallinta :pois-kytketyt-ominaisuudet])

      :laadunseuranta (component/using
                        (laadunseuranta/->Laadunseuranta)
                        [:http-palvelin :db :pois-kytketyt-ominaisuudet :fim :sonja-sahkoposti :labyrintti :pois-kytketyt-ominaisuudet])

      :tarkastukset (component/using
                      (tarkastukset/->Tarkastukset)
                      [:http-palvelin :db :pois-kytketyt-ominaisuudet :karttakuvat])

      :ilmoitukset (component/using
                     (ilmoitukset/->Ilmoitukset)
                     [:http-palvelin :db :pois-kytketyt-ominaisuudet :tloik])

      :tietyoilmoitukset (component/using
                           (tietyoilmoitukset/->Tietyoilmoitukset)
                           [:tloik :http-palvelin :db :pois-kytketyt-ominaisuudet :pdf-vienti :fim :sonja-sahkoposti])

      :turvallisuuspoikkeamat (component/using
                                (turvallisuuspoikkeamat/->Turvallisuuspoikkeamat)
                                [:http-palvelin :db :turi :pois-kytketyt-ominaisuudet])

      :integraatioloki-palvelu (component/using
                                 (integraatioloki-palvelu/->Integraatioloki)
                                 [:http-palvelin :db-replica :pois-kytketyt-ominaisuudet])
      :raportit (component/using
                  (raportit/->Raportit)
                  [:http-palvelin :db :pois-kytketyt-ominaisuudet :raportointi :pdf-vienti])

      :yha (component/using
             (yha/->Yha)
             [:http-palvelin :db :pois-kytketyt-ominaisuudet :yha-integraatio])


      :velho (component/using
               (velho/->Velho)
               [:http-palvelin :db :pois-kytketyt-ominaisuudet :velho-integraatio])

      :tr-haku (component/using
                 (tierekisteri-haku/->TierekisteriHaku)
                 [:http-palvelin :db :pois-kytketyt-ominaisuudet])

      :geometriapaivitykset (component/using
                              (geometriapaivitykset/->Geometriapaivitykset
                                (:geometriapaivitykset asetukset))
                              [:db :pois-kytketyt-ominaisuudet :integraatioloki])

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
                             [:db :pois-kytketyt-ominaisuudet :integraatioloki])


      :sonja-jms-yhteysvarmistus (component/using
                                   (let [{:keys [ajovali-minuutteina jono]} (:sonja-jms-yhteysvarmistus asetukset)]
                                     (sonja-jms-yhteysvarmistus/->SonjaJmsYhteysvarmistus ajovali-minuutteina jono))
                                   [:db :pois-kytketyt-ominaisuudet :integraatioloki :sonja])

      :tilannekuva (component/using
                     (tilannekuva/->Tilannekuva)
                     {:db :db-replica
                      :http-palvelin :http-palvelin
                      :pois-kytketyt-ominaisuudet :pois-kytketyt-ominaisuudet
                      :karttakuvat :karttakuvat
                      :fim :fim})
      :tienakyma (component/using
                   (tienakyma/->Tienakyma)
                   {:db :db-replica
                    :pois-kytketyt-ominaisuudet :pois-kytketyt-ominaisuudet
                    :http-palvelin :http-palvelin})
      :karttakuvat (component/using
                     (karttakuvat/luo-karttakuvat)
                     [:http-palvelin :db :pois-kytketyt-ominaisuudet])
      :hankkeet (component/using
                  (hankkeet/->Hankkeet)
                  [:db :pois-kytketyt-ominaisuudet :http-palvelin])
      :sopimukset (component/using
                    (sopimukset/->Sopimukset)
                    [:db :pois-kytketyt-ominaisuudet :http-palvelin])

      :urakan-tyotunnit (component/using
                          (urakan-tyotunnit/->UrakanTyotunnit)
                          [:db :pois-kytketyt-ominaisuudet :http-palvelin :turi])

      :hairioilmoitukset (component/using
                           (hairioilmoitukset/->Hairioilmoitukset)
                           [:db :http-palvelin])

      :debug (component/using
               (debug/->Debug)
               {:db :db-replica
                :http-palvelin :http-palvelin})

      :sahke (component/using
               (let [{:keys [lahetysjono uudelleenlahetysaika]} (:sahke asetukset)]
                 (sahke/->Sahke lahetysjono uudelleenlahetysaika))
               [:db :integraatioloki :sonja])

      :reimari (component/using
                 (let [{:keys [url kayttajatunnus salasana
                               toimenpidehakuvali
                               komponenttityyppihakuvali
                               turvalaitekomponenttihakuvali
                               vikahakuvali
                               turvalaiteryhmahakuaika]} (:reimari asetukset)]
                   (reimari/->Reimari url kayttajatunnus salasana
                                      toimenpidehakuvali
                                      komponenttityyppihakuvali
                                      turvalaitekomponenttihakuvali
                                      vikahakuvali
                                      turvalaiteryhmahakuaika))
                 [:db :pois-kytketyt-ominaisuudet :integraatioloki])

      :ais-data (component/using
                  (let [{:keys [url sekunnin-valein]} (:ais-data asetukset)]
                    (ais-data/->Ais-haku url sekunnin-valein))
                  [:db :pois-kytketyt-ominaisuudet :integraatioloki])

      :vkm (component/using
             (let [{url :url} (:vkm asetukset)]
               (vkm/->VKM url))
             [:db :pois-kytketyt-ominaisuudet :integraatioloki])

      :api-jarjestelmatunnukset (component/using
                                  (api-jarjestelmatunnukset/->APIJarjestelmatunnukset)
                                  [:http-palvelin :db :pois-kytketyt-ominaisuudet])

      :geometria-aineistot (component/using
                             (geometria-aineistot/->Geometria-aineistot)
                             [:http-palvelin :db])

      :organisaatiot (component/using
                       (organisaatiot/->Organisaatiot)
                       [:http-palvelin :db :pois-kytketyt-ominaisuudet])

      :koordinaatit (component/using
                      (koordinaatit/->Koordinaatit)
                      [:http-palvelin])

      :jarjestelman-tila (component/using
                   (jarjestelman-tila/->JarjestelmanTila (:kehitysmoodi asetukset))
                   [:db :http-palvelin])

      ;; Harja API
      :api-urakat (component/using
                    (api-urakat/->Urakat)
                    [:http-palvelin :db :pois-kytketyt-ominaisuudet :integraatioloki])
      :api-laatupoikkeamat (component/using
                             (api-laatupoikkeamat/->Laatupoikkeamat)
                             [:http-palvelin :db :pois-kytketyt-ominaisuudet :liitteiden-hallinta
                              :integraatioloki])
      :api-paivystajatiedot (component/using
                              (api-paivystajatiedot/->Paivystajatiedot)
                              [:http-palvelin :db :pois-kytketyt-ominaisuudet :integraatioloki])
      :api-pistetoteuma (component/using
                          (api-pistetoteuma/->Pistetoteuma)
                          [:http-palvelin :db :pois-kytketyt-ominaisuudet :integraatioloki])
      :api-reittitoteuma (component/using
                           (api-reittitoteuma/->Reittitoteuma)
                           [:http-palvelin :db :pois-kytketyt-ominaisuudet :db-replica :integraatioloki])
      :api-varustetoteuma (component/using
                            (api-varustetoteuma/->Varustetoteuma)
                            [:http-palvelin :db :pois-kytketyt-ominaisuudet :tierekisteri :integraatioloki])
      :api-siltatarkastukset (component/using
                               (api-siltatarkastukset/->Siltatarkastukset)
                               [:http-palvelin :db :pois-kytketyt-ominaisuudet :integraatioloki :liitteiden-hallinta])
      :api-tarkastukset (component/using
                          (api-tarkastukset/->Tarkastukset)
                          [:http-palvelin :db :pois-kytketyt-ominaisuudet :integraatioloki :liitteiden-hallinta])
      :api-tyokoneenseuranta (component/using
                               (api-tyokoneenseuranta/->Tyokoneenseuranta)
                               [:http-palvelin :db])
      :api-tyokoneenseuranta-puhdistus (component/using
                                         (tks-putsaus/->TyokoneenseurantaPuhdistus)
                                         [:db])
      :api-turvallisuuspoikkeama (component/using
                                   (turvallisuuspoikkeama/->Turvallisuuspoikkeama)
                                   [:http-palvelin :db :integraatioloki
                                    :liitteiden-hallinta :turi :pois-kytketyt-ominaisuudet])
      :api-suolasakkojen-lahetys (component/using
                                   (suolasakkojen-lahetys/->SuolasakkojenLahetys)
                                   [:db :pois-kytketyt-ominaisuudet])
      :api-varusteet (component/using
                       (api-varusteet/->Varusteet)
                       [:http-palvelin :db :pois-kytketyt-ominaisuudet :integraatioloki :tierekisteri :vkm])
      :api-ilmoitukset (component/using
                         (api-ilmoitukset/->Ilmoitukset)
                         [:http-palvelin :db :pois-kytketyt-ominaisuudet :integraatioloki
                          :tloik])
      :api-yllapitokohteet (component/using
                             (api-yllapitokohteet/->Yllapitokohteet)
                             [:http-palvelin :db :pois-kytketyt-ominaisuudet :integraatioloki :liitteiden-hallinta :fim :sonja-sahkoposti :vkm])
      :api-ping (component/using
                  (api-ping/->Ping)
                  [:http-palvelin :db :pois-kytketyt-ominaisuudet :integraatioloki])

      :api-yhteystiedot (component/using
                          (api-yhteystiedot/->Yhteystiedot)
                          [:http-palvelin :db :pois-kytketyt-ominaisuudet :integraatioloki :fim])

      :api-tiemerkintatoteuma (component/using
                                (api-tiemerkintatoteuma/->Tiemerkintatoteuma)
                                [:http-palvelin :db :pois-kytketyt-ominaisuudet :integraatioloki])

      :api-urakan-tyotunnit (component/using
                              (api-urakan-tyotunnit/->UrakanTyotunnit)
                              [:http-palvelin :db :pois-kytketyt-ominaisuudet :integraatioloki :turi])
      :api-tieluvat (component/using
                      (api-tieluvat/->Tieluvat)
                      [:http-palvelin :db :pois-kytketyt-ominaisuudet :integraatioloki :liitteiden-hallinta])


      :api-paikkaukset (component/using
                         (api-paikkaukset/->Paikkaukset)
                         [:http-palvelin :db :pois-kytketyt-ominaisuudet :integraatioloki :yha-paikkauskomponentti])

      :tieluvat (component/using
                  (tieluvat/->Tieluvat)
                  [:http-palvelin :db])

      ;; Ajastettu laskutusyhteenvetojen muodostus
      :laskutusyhteenvetojen-muodostus
      (component/using
        (laskutusyhteenvedot/->LaskutusyhteenvetojenMuodostus)
        [:db :pois-kytketyt-ominaisuudet])

      :status (component/using
                (status/luo-status (:kehitysmoodi asetukset))
                [:http-palvelin :db :pois-kytketyt-ominaisuudet :komponenttien-tila])

      :vaylien-geometriahaku
      (component/using
        (let [asetukset (:vaylat asetukset)]
          (vaylien-geometriat/->VaylienGeometriahaku
            (:geometria-url asetukset)
            (:paivittainen-tarkistusaika asetukset)
            (:paivitysvali-paivissa asetukset)))
        [:db :pois-kytketyt-ominaisuudet :http-palvelin :integraatioloki])

      :kanavasiltojen-geometriahaku
      (component/using
        (let [asetukset (:kanavasillat asetukset)]
          (kanavasiltojen-geometriat/->KanavasiltojenGeometriahaku
            (:geometria-url asetukset)
            (:paivittainen-tarkistusaika asetukset)
            (:paivitysvali-paivissa asetukset)))
        [:db :pois-kytketyt-ominaisuudet :http-palvelin :integraatioloki])

      :mobiili-laadunseuranta
      (component/using
        (harja-laadunseuranta/->Laadunseuranta)
        [:db :pois-kytketyt-ominaisuudet :http-palvelin])

      :urakan-tyotuntimuistutukset
      (component/using
        (urakan-tyotuntimuistutukset/->UrakanTyotuntiMuistutukset
          (get-in asetukset [:tyotunti-muistutukset :paivittainen-aika]))
        [:db :pois-kytketyt-ominaisuudet :sonja-sahkoposti :fim]))))

(defonce harja-jarjestelma nil)

(defn aloita-sonja [jarjestelma]
  (go
    (log/info "Aloitaetaan Sonjayhteys")
    (loop []
      (let [{:keys [vastaus virhe kaskytysvirhe]} (<! (sonja/kasky (:sonja jarjestelma) {:aloita-yhteys nil}))]
        (when vastaus
          (log/info "Sonja yhteys aloitettu"))
        (when kaskytysvirhe
          (log/error "Sonjayhteyden aloittamisessa käskytysvirhe: " kaskytysvirhe))
        (<! (timeout 2000))
        (if (or virhe (= :kasykytyskanava-taynna kaskytysvirhe))
          (recur)
          vastaus)))))

(defn- merkkaa-kaynnistyminen! []
  (log/debug "Merkataan HARJAn käynnistyminen")
  (event-apurit/julkaise-tapahtuma :harja-tila
                                   {:viesti "Harja käynnistyy"
                                    :kaikki-ok? false}))

(defn- merkkaa-kaynnistetyksi! []
  (log/debug "Merkataan HARJA käynnistetyksi")
  (event-apurit/julkaise-tapahtuma :harja-tila
                                   {:viesti "Harja käynnistetty"
                                    :kaikki-ok? true}))

(defn- kaynnista-pelkastaan-jarjestelma
  ([]
   (let [asetukset (lue-asetukset asetukset-tiedosto)]
     (kaynnista-pelkastaan-jarjestelma asetukset)))
  ([asetukset]
   (alter-var-root #'harja-jarjestelma
                   (fn [_]
                     (let [jarjestelma (-> asetukset
                                           luo-jarjestelma
                                           component/start)]
                       (aloita-sonja jarjestelma)
                       jarjestelma)))))

(defn- kuuntele-tapahtumia! []
  (event-apurit/tarkkaile-tapahtumaa :harjajarjestelman-restart
                                     {}
                                     (fn [{:keys [palvelin payload]}]
                                       (when (= palvelin event-apurit/host-nimi)
                                         (if (= payload :all)
                                           (kaynnista-pelkastaan-jarjestelma)
                                           (when (nil? (alter-var-root #'harja-jarjestelma
                                                                       (fn [harja-jarjestelma]
                                                                         (println "----- :harjajarjestelman-restart ----")
                                                                         (println "payload: " payload)
                                                                         (try (let [uudelleen-kaynnistetty-jarjestelma (jarjestelma/system-restart harja-jarjestelma payload)]
                                                                                (println "----- UUDELLEEN KÄYNNISTYS ----")
                                                                                (println (str (jarjestelma/kaikki-ok? uudelleen-kaynnistetty-jarjestelma)))
                                                                                (if (jarjestelma/kaikki-ok? uudelleen-kaynnistetty-jarjestelma)
                                                                                  (do (aloita-sonja uudelleen-kaynnistetty-jarjestelma)
                                                                                      (event-apurit/julkaise-tapahtuma :harjajarjestelman-restart-onnistui tapahtumien-tulkkaus/tyhja-arvo))
                                                                                  (event-apurit/julkaise-tapahtuma :harjajarjestelman-restart-epaonnistui tapahtumien-tulkkaus/tyhja-arvo))
                                                                                uudelleen-kaynnistetty-jarjestelma)
                                                                              (catch Throwable t
                                                                                (log/error "Harjajärjestelmän uudelleen käynnistyksessä virhe: " (.getMessage t) ".\nStack: " (.printStackTrace t))
                                                                                (event-apurit/julkaise-tapahtuma :harjajarjestelman-restart-epaonnistui tapahtumien-tulkkaus/tyhja-arvo)
                                                                                nil)))))
                                             (kaynnista-pelkastaan-jarjestelma)))))))

(defn- kaynnista-harja-tarkkailija! [asetukset]
  (tarkkailija/kaynnista! asetukset))

(defn- sammuta-harja-tarkkailija! []
  (tarkkailija/sammuta!))

(defn kaynnista-jarjestelma [asetusfile lopeta-jos-virhe?]
  (try
    (let [asetukset (lue-asetukset asetusfile)]
      ;; Säikeet vain sammuvat, jos niissä nakataan jotain eikä sitä käsitellä siinä säikeessä. Tämä koodinpätkä
      ;; ottaa kaikki tällaiset throwablet kiinni ja logittaa sen.
      (Thread/setDefaultUncaughtExceptionHandler
        (reify Thread$UncaughtExceptionHandler
          (uncaughtException [_ thread e]
            (log/error e "Säije " (.getName thread) " kaatui virheeseen: " (.getMessage e))
            (log/error "Virhe: " e))))

      (konfiguroi-lokitus asetukset)
      (if-let [virheet (tarkista-asetukset asetukset)]
        (log/error "Validointivirhe asetuksissa:" virheet))
      (tarkista-ymparisto!)
      (kaynnista-harja-tarkkailija! asetukset)
      (kuuntele-tapahtumia!)
      (merkkaa-kaynnistyminen!)
      (kaynnista-pelkastaan-jarjestelma asetukset)
      (aloita-sonja harja-jarjestelma)
      (merkkaa-kaynnistetyksi!))
    (catch Throwable t
      (log/fatal t "Harjan käynnistyksessä virhe")
      (when lopeta-jos-virhe?
        (System/exit 1)))))

(defn sammuta-jarjestelma []
  (sammuta-harja-tarkkailija!)
  (when harja-jarjestelma
    (alter-var-root #'harja-jarjestelma (fn [s]
                                          (component/stop s)
                                          nil))))

(defn -main [& argumentit]
  (kaynnista-jarjestelma (or (first argumentit) asetukset-tiedosto) true)
  (.addShutdownHook (Runtime/getRuntime) (Thread. sammuta-jarjestelma)))
