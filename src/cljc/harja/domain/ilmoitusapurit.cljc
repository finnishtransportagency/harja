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
   :tyomaajarjestelyihinLiittyvaIlmoitus        "Työmaajärjestelyihin liittyvä ilmoitus"
   :kuoppiaTiessa                               "Kuoppia tiessä"
   :kelikysely                                  "Kelikysely"
   :soratienKuntoHuono                          "Soratien kunto huono"
   :saveaTiella                                 "Savea tiellä"
   :liikennettaVaarantavaEsteTiella             "Liikennettä vaarantava este tiellä"
   :irtokiviaTiella                             "Irtokiviä tiellä"
   :kevyenLiikenteenVaylaanLiittyvaIlmoitus     "Kevyen liikenteen väylä"
   :raivausJaKorjaustoita                       "Raivaus ja korjaustöitä"
   :auraustarve                                 "Auraustarve"
   :kaivonKansiRikki                            "Kaivon kansi rikki"
   :kevyenLiikenteenVaylatOvatLiukkaita         "Kevyen liikenteen väylät ovat liukkaita"
   :routaheitto                                 "Routaheitto"
   :avattavatPuomit                             "Avattavat puomit"
   :tievalaistusVioittunutOnnettomuudessa       "Tievalaistus vioittunut onnettomuudessa"
   :muuKyselyTaiNeuvonta                        "Muu kysely tai neuvonta"
   :soratienTasaustarve                         "Soratien tasaustarve"
   :tieTaiTienReunaOnPainunut                   "Tie tai tien reuna on painunut"
   :siltaanLiittyvaIlmoitus                     "Siltaan liittyvä ilmoitus"
   :polynsidontatarve                           "Pölynsidontatarve"
   :liikennevalotEivatToimi                     "Liikennevalot eivät toimi"
   :kunnossapitoJaHoitotyo                      "Kunnossapito- ja hoitotyö"
   :vettaTiella                                 "Vettä tiellä"
   :aurausvallitNakemaesteena                   "Aurausvallit näkemäesteenä"
   :ennakoivaVaroitus                           "Ennakoiva varoitus"
   :levahdysalueeseenLiittyvaIlmoitus           "Levähdysalueeseen liittyvä ilmoitus"
   :sohjonPoisto                                "Sohjon poisto"
   :liikennekeskusKuitannutLoppuneeksi          "Liikennekeskus kuitannut loppuneeksi"
   :muuToimenpidetarve                          "Muu toimenpidetarve"
   :hiekoitustarve                              "Hiekoitustarve"
   :tietOvatJaatymassa                          "Tiet ovat jäätymässä"
   :jaatavaaSadetta                             "Jäätävää sadetta"
   :tienvarsilaitteisiinLiittyvaIlmoitus        "Tienvarsilaitteisiin liittyvä ilmoitus"
   :oljyaTiella                                 "Öljyä tiellä"
   :sahkojohtoOnPudonnutTielle                  "Sähköjohto on pudonnut tielle"
   :tieOnSortunut                               "Tie on sortunut"
   :tievalaistusVioittunut                      "Tievalaistus vioittunut"
   :testilahetys                                "Testilähetys"
   :tievalaistuksenLamppujaPimeana              "Tievalaistuksen lamppuja pimeänä"
   :virkaApupyynto                              "Virka-apupyynto"
   :tiemerkintoihinLiittyvaIlmoitus             "Tiemerkintöihin liittyvä ilmoitus"
   :tulvavesiOnNoussutTielle                    "Tulvavesi on noussut tielle"
   :niittotarve                                 "Niittotarve"
   :kuormaOnLevinnytTielle                      "Kuorma on levinnyt tielle"
   :tieOnLiukas                                 "Tie on liukas"
   :tiellaOnEste                                "Tiellä on este"
   :harjaustarve                                "Harjaustarve"
   :hoylaystarve                                "Höyläystarve"
   :tietyokysely                                "Tietyökysely"
   :paallystevaurio                             "Päällystevaurio"
   :rikkoutunutAjoneuvoTiella                   "Rikkoutunut ajoneuvo tiellä"
   :mustaaJaataTiella                           "Mustaa jäätä tiellä"
   :kevyenLiikenteenVaylillaOnLunta             "Kevyen liikenteen väylillä on lunta"
   :hirviaitaVaurioitunut                       "Hirviaita vaurioitunut"
   :korvauskysely                               "Korvauskysely"
   :puitaOnKaatunutTielle                       "Puita on kaatunut tielle"
   :rumpuunLiittyvaIlmoitus                     "Rumpuun liittyva ilmoitus"
   :lasiaTiella                                 "Lasia tiellä"
   :liukkaudentorjuntatarve                     "Liukkaudentorjuntatarve"
   :alikulkukaytavassaVetta                     "Alikulkukäytävässä vetta"
   :tievalaistuksenLamppuPimeana                "Tievalaistuksen lamppu pimeänä"
   :kevyenLiikenteenVaylatOvatJaisiaJaLiukkaita "Kevyen liikenteen väylät ovat jäisiä ja liukkaita"
   :kuoppa                                      "Kuoppa"
   :toimenpidekysely                            "Toimenpidekysely"
   :pysakkiinLiittyvaIlmoitus                   "Pysäkkiin liittyvä ilmoitus"
   :nakemaalueenRaivaustarve                    "Näkemäalueen raivaustarve"
   :vesakonraivaustarve                         "Vesakonraivaustarve"
   :muuttuvatOpasteetEivatToimi                 "Muuttuvat opasteet eivät toimi"
   :tievalaistus                                "Tievalaistus"
   :vesiSyovyttanytTienReunaa                   "Vesi syövyttänyt tien reunaa"
   :raskasAjoneuvoJumissa                       "Raskas ajoneuvo jumissa"
   :myrskyvaurioita                             "Myrskyvaurioita"
   :kaidevaurio                                 "Kaidevaurio"
   :liikennemerkkeihinLiittyvaIlmoitus          "Liikennemerkkeihin liittyvä ilmoitus"})

(defn parsi-selitteet [selitteet]
  (string/join ", "
               (mapv #(get +ilmoitusten-selitteet+ %)
                     selitteet)))

(def +ilmoitustilat+ #{:suljetut :avoimet})