(ns harja.domain.ilmoitusapurit
  "Selain- ja palvelinpuolen yhteisiä ilmoituksiin liittyviä asioita"
  (:require
    #?(:cljs [harja.loki :refer [log]])
    [clojure.string :as string]))

(def +ilmoitustyypit+ #{:kysely :toimenpidepyynto :tiedoitus})

(def kuittaustyypit
  [:vastaanotto :aloitus :lopetus :muutos :vastaus])

(def kuittaustyypin-selite
  {:vastaanotto "Vastaanotettu"
   :aloitus     "Aloitettu"
   :lopetus     "Lopetettu"
   :muutos      "Muutos"
   :vastaus     "Vastaus"})

(defn ilmoitustyypin-nimi
  [tyyppi]
  (case tyyppi
    :kysely "Kysely"
    :toimenpidepyynto "Toimenpide\u00ADpyyntö"
    :tiedoitus "Tiedoksi"))

(defn ilmoitustyypin-lyhenne
  [tyyppi]
  (case tyyppi
    :kysely "URK"
    :toimenpidepyynto "TPP"
    :tiedoitus "TUR"))

(defn ilmoitustyypin-lyhenne-ja-nimi
  [tyyppi]
  (str (ilmoitustyypin-lyhenne tyyppi) " (" (ilmoitustyypin-nimi tyyppi) ")"))

(defn nayta-henkilo
  "Palauttaa merkkijonon mallia 'Etunimi Sukunimi, Organisaatio Y1234'"
  [henkilo]
  (when henkilo
    (str
      (:etunimi henkilo)
      (when (and (:etunimi henkilo) (:sukunimi henkilo)) " ")
      (:sukunimi henkilo)
      (when
        (and
          (or (:etunimi henkilo) (:sukunimi henkilo))
          (or (:organisaatio henkilo) (:ytunnus henkilo)))

        ", ")
      (:organisaatio henkilo)
      (when (and (:ytunnus henkilo) (:organisaatio henkilo)) " ")
      (:ytunnus henkilo))))

(defn parsi-puhelinnumero
  [henkilo]
  (let [tp (:tyopuhelin henkilo)
        mp (:matkapuhelin henkilo)
        puh (:puhelinnumero henkilo)
        tulos (when henkilo
                (str
                  (if puh                                   ;; Jos puhelinnumero löytyy, käytetään vaan sitä
                    (str puh)
                    (when (or tp mp)
                      (if (and tp mp (not (= tp mp)))       ;; Jos on matkapuhelin JA työpuhelin, ja ne ovat erit..
                        (str tp " / " mp)

                        (str (or mp tp)))                   ;; Muuten käytetään vaan jompaa kumpaa

                      ))))]
    (if (empty? tulos) nil tulos)))

(defn parsi-yhteystiedot
  "Palauttaa merkkijonon, jossa on henkilön puhelinnumero(t) ja sähköposti.
  Ilmoituksen lähettäjällä on vain 'puhelinnumero', muilla voi olla matkapuhelin ja/tai työpuhelin."
  [henkilo]
  (let [puhelin (parsi-puhelinnumero henkilo)
        sp (:sahkoposti henkilo)
        tulos (when henkilo
                (str
                  (or puhelin)
                  (when (and puhelin sp) ", ")
                  (when sp (str sp))))]
    (if (empty? tulos) nil tulos)))


(def +ilmoitusten-selitteet+
  {nil                                          "Kaikki"
   :tyomaajarjestelyihinLiittyvaIlmoitus        "työmaajärjestelyihin liittyvä ilmoitus"
   :kuoppiaTiessa                               "kuoppia tiessä"
   :kelikysely                                  "kelikysely"
   :soratienKuntoHuono                          "soratien kunto huono"
   :saveaTiella                                 "savea tiellä"
   :liikennettaVaarantavaEsteTiella             "liikennettä vaarantava este tiellä"
   :irtokiviaTiella                             "irtokiviä tiellä"
   :kevyenLiikenteenVaylaanLiittyvaIlmoitus     "kevyen liikenteen väylää"
   :raivausJaKorjaustoita                       "raivaus ja korjaustöitä"
   :auraustarve                                 "auraustarve"
   :kaivonKansiRikki                            "kaivon kansi rikki"
   :kevyenLiikenteenVaylatOvatLiukkaita         "kevyen liikenteen väylät ovat liukkaita"
   :routaheitto                                 "routaheitto"
   :avattavatPuomit                             "avattavat puomit"
   :tievalaistusVioittunutOnnettomuudessa       "tievalaistus vioittunut onnettomuudessa"
   :muuKyselyTaiNeuvonta                        "muu kysely tai neuvonta"
   :soratienTasaustarve                         "soratien tasaustarve"
   :tieTaiTienReunaOnPainunut                   "tie tai tien reuna on painunut"
   :siltaanLiittyvaIlmoitus                     "siltaanl liittyva ilmoitus"
   :polynsidontatarve                           "pölynsidontatarve"
   :liikennevalotEivatToimi                     "liikennevalot eivät toimi"
   :kunnossapitoJaHoitotyo                      "kunnossapito- ja hoitotyo"
   :vettaTiella                                 "vettä tiellä"
   :aurausvallitNakemaesteena                   "aurausvallit näkemäesteena"
   :ennakoivaVaroitus                           "ennakoiva varoitus"
   :levahdysalueeseenLiittyvaIlmoitus           "levahdysalueeseen liittyva ilmoitus"
   :sohjonPoisto                                "sohjon poisto"
   :liikennekeskusKuitannutLoppuneeksi          "liikennekeskus kuitannut loppuneeksi"
   :muuToimenpidetarve                          "muu toimenpidetarve"
   :hiekoitustarve                              "hiekoitustarve"
   :tietOvatJaatymassa                          "tiet ovat jäätymässä"
   :jaatavaaSadetta                             "jäätävää sadetta"
   :tienvarsilaitteisiinLiittyvaIlmoitus        "tienvarsilaitteisiin liittyvä ilmoitus"
   :oljyaTiella                                 "öljyä tiellä"
   :sahkojohtoOnPudonnutTielle                  "sähköjohto on pudonnut tielle"
   :tieOnSortunut                               "tie on sortunut"
   :tievalaistusVioittunut                      "tievalaistus vioittunut"
   :testilahetys                                "testilahetys"
   :tievalaistuksenLamppujaPimeana              "tievalaistuksen lamppuja pimeänä"
   :virkaApupyynto                              "virka-apupyynto"
   :tiemerkintoihinLiittyvaIlmoitus             "tiemerkintöihin liittyvä ilmoitus"
   :tulvavesiOnNoussutTielle                    "tulvavesi on noussut tielle"
   :niittotarve                                 "niittotarve"
   :kuormaOnLevinnytTielle                      "kuorma on levinnyt tielle"
   :tieOnLiukas                                 "tie on liukas"
   :tiellaOnEste                                "tiellä on este"
   :harjaustarve                                "harjaustarve"
   :hoylaystarve                                "höyläystarve"
   :tietyokysely                                "tietyokysely"
   :paallystevaurio                             "päällystevaurio"
   :rikkoutunutAjoneuvoTiella                   "rikkoutunut ajoneuvo tiellä"
   :mustaaJaataTiella                           "mustaa jäätä tiellä"
   :kevyenLiikenteenVaylillaOnLunta             "kevyen liikenteen väylilla on lunta"
   :hirviaitaVaurioitunut                       "hirviaita vaurioitunut"
   :korvauskysely                               "korvauskysely"
   :puitaOnKaatunutTielle                       "puita on kaatunut tielle"
   :rumpuunLiittyvaIlmoitus                     "rumpuun liittyva ilmoitus"
   :lasiaTiella                                 "lasia tiellä"
   :liukkaudentorjuntatarve                     "liukkaudentorjuntatarve"
   :alikulkukaytavassaVetta                     "alikulkukäytävässä vetta"
   :tievalaistuksenLamppuPimeana                "tievalaistuksen lamppu pimeänä"
   :kevyenLiikenteenVaylatOvatJaisiaJaLiukkaita "kevyen liikenteen vaylat ovat jäisiä ja liukkaita"
   :kuoppa                                      "kuoppa"
   :toimenpidekysely                            "toimenpidekysely"
   :pysakkiinLiittyvaIlmoitus                   "pysakkiin liittyva ilmoitus"
   :nakemaalueenRaivaustarve                    "näkemäalueen raivaustarve"
   :vesakonraivaustarve                         "vesakonraivaustarve"
   :muuttuvatOpasteetEivatToimi                 "muuttuvat opasteet eivät toimi"
   :tievalaistus                                "tievalaistus"
   :vesiSyovyttanytTienReunaa                   "vesi syövyttanyt tien reunaa"
   :raskasAjoneuvoJumissa                       "raskas ajoneuvo jumissa"
   :myrskyvaurioita                             "myrskyvaurioita"
   :kaidevaurio                                 "kaidevaurio"
   :liikennemerkkeihinLiittyvaIlmoitus          "liikennemerkkeihin liittyvä ilmoitus"})

(defn parsi-selitteet [selitteet]
  (string/join ", "
               (mapv #(get +ilmoitusten-selitteet+ %)
                     selitteet)))

(def +ilmoitustilat+ #{:suljetut :avoimet})