(ns harja.domain.ilmoitusapurit
  "Selain- ja palvelinpuolen yhteisiä ilmoituksiin liittyviä asioita"
  (:require
    #?(:cljs [harja.loki :refer [log]])))


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

(defn parsi-selitteet [selitteet]
  #?(:cljs (log "-----> SELITTEET:" (pr-str selitteet)))
  (clojure.string/join ", "
                       (mapv #(case %
                               :tyomaajarjestelyihinLiittyvaIlmoitus "työmaajärjestelyt"
                               :kuoppiaTiessa "kuoppia tiessa"
                               :kelikysely "kelikysely"
                               :soratienKuntoHuono "soratien kunto huono"
                               :saveaTiella "savea tiellä"
                               :liikennettaVaarantavaEsteTiella "liikennettä vaarantava este tiella"
                               :irtokiviaTiella "irtokiviä tiella"
                               :kevyenLiikenteenVaylaanLiittyvaIlmoitus "kevyen liikenteen väylää"
                               :raivausJaKorjaustoita "raivaus ja korjaustöitä"
                               :auraustarve "auraustarve"
                               :kaivonKansiRikki "kaivon kansiRikki"
                               :kevyenLiikenteenVaylatOvatLiukkaita "kevyenLiikenteenVaylatOvatLiukkaita"
                               :routaheitto "routaheitto"
                               :avattavatPuomit "avattavatPuomit"
                               :tievalaistusVioittunutOnnettomuudessa "tievalaistusVioittunutOnnettomuudessa"
                               :muuKyselyTaiNeuvonta "muuKyselyTaiNeuvonta"
                               :soratienTasaustarve "soratienTasaustarve"
                               :tieTaiTienReunaOnPainunut "tieTaiTienReunaOnPainunut"
                               :siltaanLiittyvaIlmoitus "siltaanLiittyvaIlmoitus"
                               :polynsidontatarve "polynsidontatarve"
                               :liikennevalotEivatToimi "liikennevalotEivatToimi"
                               :kunnossapitoJaHoitotyo "kunnossapitoJaHoitotyo"
                               :vettaTiella "vettaTiella"
                               :aurausvallitNakemaesteena "aurausvallitNakemaesteena"
                               :ennakoivaVaroitus "ennakoivaVaroitus"
                               :levahdysalueeseenLiittyvaIlmoitus "levahdysalueeseenLiittyvaIlmoitus"
                               :sohjonPoisto "sohjonPoisto"
                               :liikennekeskusKuitannutLoppuneeksi "liikennekeskusKuitannutLoppuneeksi"
                               :muuToimenpidetarve "muuToimenpidetarve"
                               :hiekoitustarve "hiekoitustarve"
                               :tietOvatJaatymassa "tietOvatJaatymassa"
                               :jaatavaaSadetta "jaatavaaSadetta"
                               :tienvarsilaitteisiinLiittyvaIlmoitus "tienvarsilaitteisiinLiittyvaIlmoitus"
                               :oljyaTiella "oljyaTiella"
                               :sahkojohtoOnPudonnutTielle "sahkojohtoOnPudonnutTielle"
                               :tieOnSortunut "tieOnSortunut"
                               :tievalaistusVioittunut "tievalaistusVioittunut"
                               :testilahetys "testilahetys"
                               :tievalaistuksenLamppujaPimeana "tievalaistuksenLamppujaPimeana"
                               :virkaApupyynto "virkaApupyynto"
                               :tiemerkintoihinLiittyvaIlmoitus "tiemerkintoihinLiittyvaIlmoitus"
                               :tulvavesiOnNoussutTielle "tulvavesiOnNoussutTielle"
                               :niittotarve "niittotarve"
                               :kuormaOnLevinnytTielle "kuormaOnLevinnytTielle"
                               :tieOnLiukas "tieOnLiukas"
                               :tiellaOnEste "tiellaOnEste"
                               :harjaustarve "harjaustarve"
                               :hoylaystarve "hoylaystarve"
                               :tietyokysely "tietyokysely"
                               :paallystevaurio "paallystevaurio"
                               :rikkoutunutAjoneuvoTiella "rikkoutunutAjoneuvoTiella"
                               :mustaaJaataTiella "mustaaJaataTiella"
                               :kevyenLiikenteenVaylillaOnLunta "kevyenLiikenteenVaylillaOnLunta"
                               :hirviaitaVaurioitunut "hirviaitaVaurioitunut"
                               :korvauskysely "korvauskysely"
                               :puitaOnKaatunutTielle "puitaOnKaatunutTielle"
                               :rumpuunLiittyvaIlmoitus "rumpuunLiittyvaIlmoitus"
                               :lasiaTiella "lasiaTiella"
                               :liukkaudentorjuntatarve "liukkaudentorjuntatarve"
                               :alikulkukaytavassaVetta "alikulkukaytavassaVetta"
                               :tievalaistuksenLamppuPimeana "tievalaistuksenLamppuPimeana"
                               :kevyenLiikenteenVaylatOvatJaisiaJaLiukkaita "kevyenLiikenteenVaylatOvatJaisiaJaLiukkaita"
                               :kuoppa "kuoppa"
                               :toimenpidekysely "toimenpidekysely"
                               :pysakkiinLiittyvaIlmoitus "pysakkiinLiittyvaIlmoitus"
                               :nakemaalueenRaivaustarve "nakemaalueenRaivaustarve"
                               :vesakonraivaustarve "vesakonraivaustarve"
                               :muuttuvatOpasteetEivatToimi "muuttuvatOpasteetEivatToimi"
                               :tievalaistus "tievalaistus"
                               :vesiSyovyttanytTienReunaa "vesiSyovyttanytTienReunaa"
                               :raskasAjoneuvoJumissa "raskasAjoneuvoJumissa"
                               :myrskyvaurioita "myrskyvaurioita"
                               :kaidevaurio "kaidevaurio"
                               :liikennemerkkeihinLiittyvaIlmoitus "liikennemerkkeihinLiittyvaIlmoitus") selitteet)))

(def +ilmoitustilat+ #{:suljetut :avoimet})