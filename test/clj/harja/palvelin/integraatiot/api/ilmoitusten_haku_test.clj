(ns harja.palvelin.integraatiot.api.ilmoitusten-haku-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [com.stuartsierra.component :as component]
            [harja.testi :refer :all]
            [harja.jms-test :refer [feikki-sonja]]
            [harja.palvelin.integraatiot.tloik.tyokalut :refer :all]
            [harja.palvelin.integraatiot.tloik.tloik-komponentti :refer [->Tloik]]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [cheshire.core :as cheshire]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.integraatiot.api.ilmoitukset :as api-ilmoitukset]
            [harja.tyokalut.xml :as xml]
            [clojure.data.zip.xml :as z])
  (:import (java.net URLEncoder)
           (java.text SimpleDateFormat)
           (java.util Date)))

(def kayttaja "yit-rakennus")

(def +kuittausjono+ "tloik-ilmoituskuittausjono")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :api-ilmoitukset (component/using
                       (api-ilmoitukset/->Ilmoitukset)
                       [:http-palvelin :db :integraatioloki :klusterin-tapahtumat])
    :sonja (feikki-sonja)
    :tloik (component/using
             (luo-tloik-komponentti)
             [:db :sonja :integraatioloki :klusterin-tapahtumat])))

(use-fixtures :each jarjestelma-fixture)

(def odotettu-ilmoitus
  {"ilmoitettu" "2015-09-29T14:49:45Z"
   "ilmoittaja" {"email" "matti.meikalainen@palvelu.fi"
                 "etunimi" "Matti"
                 "matkapuhelin" "08023394852"
                 "sukunimi" "Meikäläinen"}
   "ilmoitusid" 123456789
   "tunniste" "UV-1509-1a"
   "ilmoitustyyppi" "toimenpidepyynto"
   "lahettaja" {"email" "pekka.paivystaja@livi.fi"
                "etunimi" "Pekka"
                "sukunimi" "Päivystäjä"}
   "lisatieto" "Vanhat vallit ovat liian korkeat ja uutta lunta on satanut reippaasti."
   "otsikko" "Korkeat vallit"
   "paikankuvaus" "Jossain kentällä."
   "selitteet" [{"selite" "tyomaajarjestelyihinLiittyvaIlmoitus"}
                {"selite" "kuoppiaTiessa"}
                {"selite" "kelikysely"}
                {"selite" "soratienKuntoHuono"}
                {"selite" "saveaTiella"}
                {"selite" "liikennettaVaarantavaEsteTiella"}
                {"selite" "irtokiviaTiella"}
                {"selite" "kevyenLiikenteenVaylaanLiittyvaIlmoitus"}
                {"selite" "raivausJaKorjaustoita"}
                {"selite" "auraustarve"}
                {"selite" "yliauraus"}
                {"selite" "kaivonKansiRikki"}
                {"selite" "kevyenLiikenteenVaylatOvatLiukkaita"}
                {"selite" "routaheitto"}
                {"selite" "avattavatPuomit"}
                {"selite" "tievalaistusVioittunutOnnettomuudessa"}
                {"selite" "muuKyselyTaiNeuvonta"}
                {"selite" "soratienTasaustarve"}
                {"selite" "tieTaiTienReunaOnPainunut"}
                {"selite" "siltaanLiittyvaIlmoitus"}
                {"selite" "polynsidontatarve"}
                {"selite" "liikennevalotEivatToimi"}
                {"selite" "kunnossapitoJaHoitotyo"}
                {"selite" "vettaTiella"}
                {"selite" "aurausvallitNakemaesteena"}
                {"selite" "ennakoivaVaroitus"}
                {"selite" "levahdysalueeseenLiittyvaIlmoitus"}
                {"selite" "sohjonPoisto"}
                {"selite" "liikennekeskusKuitannutLoppuneeksi"}
                {"selite" "muuToimenpidetarve"}
                {"selite" "hiekoitustarve"}
                {"selite" "tietOvatJaatymassa"}
                {"selite" "jaatavaaSadetta"}
                {"selite" "tienvarsilaitteisiinLiittyvaIlmoitus"}
                {"selite" "oljyaTiella"}
                {"selite" "sahkojohtoOnPudonnutTielle"}
                {"selite" "tieOnSortunut"}
                {"selite" "tievalaistusVioittunut"}
                {"selite" "testilahetys"}
                {"selite" "tievalaistuksenLamppujaPimeana"}
                {"selite" "virkaApupyynto"}
                {"selite" "tiemerkintoihinLiittyvaIlmoitus"}
                {"selite" "tulvavesiOnNoussutTielle"}
                {"selite" "niittotarve"}
                {"selite" "kuormaOnLevinnytTielle"}
                {"selite" "tieOnLiukas"}
                {"selite" "tiellaOnEste"}
                {"selite" "harjaustarve"}
                {"selite" "hoylaystarve"}
                {"selite" "tietyokysely"}
                {"selite" "paallystevaurio"}
                {"selite" "rikkoutunutAjoneuvoTiella"}
                {"selite" "mustaaJaataTiella"}
                {"selite" "kevyenLiikenteenVaylillaOnLunta"}
                {"selite" "hirviaitaVaurioitunut"}
                {"selite" "korvauskysely"}
                {"selite" "puitaOnKaatunutTielle"}
                {"selite" "rumpuunLiittyvaIlmoitus"}
                {"selite" "lasiaTiella"}
                {"selite" "liukkaudentorjuntatarve"}
                {"selite" "alikulkukaytavassaVetta"}
                {"selite" "kevyenliikenteenAlikulkukaytavassaVetta"}
                {"selite" "tievalaistuksenLamppuPimeana"}
                {"selite" "kevyenLiikenteenVaylatOvatJaisiaJaLiukkaita"}
                {"selite" "kuoppa"}
                {"selite" "toimenpidekysely"}
                {"selite" "pysakkiinLiittyvaIlmoitus"}
                {"selite" "nakemaalueenRaivaustarve"}
                {"selite" "vesakonraivaustarve"}
                {"selite" "muuttuvatOpasteetEivatToimi"}
                {"selite" "tievalaistus"}
                {"selite" "vesiSyovyttanytTienReunaa"}
                {"selite" "raskasAjoneuvoJumissa"}
                {"selite" "myrskyvaurioita"}
                {"selite" "kaidevaurio"}
                {"selite" "liikennemerkkeihinLiittyvaIlmoitus"}
                {"selite" "siirrettavaAjoneuvo"}
                {"selite" "tielleOnVuotanutNestettaLiikkuvastaAjoneuvosta"}
                {"selite" "tapahtumaOhi"}
                {"selite" "kevyenLiikenteenVaylatOvatjaatymassa"}
                {"selite" "tietOvatjaisiaJamarkia"}]
   "sijainti" {"koordinaatit" {"x" 452935.0
                               "y" 7186873.0}}
   "tienumero" 4
   "yhteydenottopyynto" false})


(deftest kuuntele-urakan-ilmoituksia
  (let [vastaus (future (api-tyokalut/get-kutsu ["/api/urakat/4/ilmoitukset?odotaUusia=true"] kayttaja portti))
        tloik-kuittaukset (atom [])]
    (sonja/laheta (:sonja jarjestelma) +tloik-ilmoitusviestijono+ +testi-ilmoitus-sanoma+)
    (sonja/kuuntele! (:sonja jarjestelma) +kuittausjono+ #(swap! tloik-kuittaukset conj (.getText %)))

    (odota-ehdon-tayttymista #(realized? vastaus) "Saatiin vastaus ilmoitushakuun." 20000)
    (is (= 200 (:status @vastaus)))

    (let [vastausdata (cheshire/decode (:body @vastaus))
          ilmoitus (get (first (get vastausdata "ilmoitukset")) "ilmoitus")]
      (is (= 1 (count (get vastausdata "ilmoitukset"))))
      (is (= odotettu-ilmoitus ilmoitus)))

    (odota-ehdon-tayttymista #(= 1 (count @tloik-kuittaukset)) "Kuittaus on vastaanotettu." 20000)

    (let [xml (first @tloik-kuittaukset)
          data (xml/lue xml)]
      (is (= "valitetty" (z/xml1-> data :kuittaustyyppi z/text))))))

(deftest hae-muuttuneet-ilmoitukset
  (u (str "UPDATE ilmoitus SET muokattu = NOW() + INTERVAL '1 hour'
           WHERE urakka = 4 AND id IN (SELECT id FROM ilmoitus WHERE urakka = 4 LIMIT 1)"))

  (let [nyt (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") (Date.))
        vastaus (api-tyokalut/get-kutsu ["/api/urakat/4/ilmoitukset?muuttunutJalkeen=" (URLEncoder/encode nyt)]
                                        kayttaja portti)
        kaikkien-ilmoitusten-maara-suoraan-kannasta (ffirst (q (str "SELECT count(*) FROM ilmoitus
                                                                     WHERE urakka = 4;")))]
    (is (= 200 (:status vastaus)))

    (let [vastausdata (cheshire/decode (:body vastaus))
          ilmoitukset (get vastausdata "ilmoitukset")
          ilmoituksia (count ilmoitukset)]
      (is (> kaikkien-ilmoitusten-maara-suoraan-kannasta ilmoituksia))
      (is (< 0 ilmoituksia)))))
