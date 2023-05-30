(ns harja.domain.tieliikenneilmoitukset
  "Selain- ja palvelinpuolen yhteisiä tieliikenneilmoituksiin liittyviä asioita"
  (:require
    #?(:cljs [cljs-time.core :as t])
    #?(:clj [clj-time.core :as t])
            [clojure.string :as string]))

(def +ilmoitustyypit+ #{:kysely :toimenpidepyynto :tiedoitus})

(def kuittaustyypit
  [:vastaanotto :aloitus :lopetus :muutos :vastaus :vaara-urakka])

(defn valitysviesti? [kuittaus]
  (#{:valitys} (:kuittaustyyppi kuittaus)))

(def tilan-selite
  {:kuittaamaton "Kuittaamaton"
   :vastaanotettu "Vastaanotettu"
   :aloitettu "Aloitettu"
   :lopetettu "Lopetettu"})

(def vaikutuksen-selite
  {:myohassa "Myöhästyneet ilmoitukset"
   :aiheutti-toimenpiteita "Toimenpiteitä aiheuttaneet ilmoitukset"})

(def kuittaustyypin-otsikko
  {:vastaanotto "Vastaanotto"
   :aloitus "Aloitus"
   :lopetus "Lopetus"
   :muutos "Muutos"
   :vastaus "Vastaus"
   :valitys "Välitys"
   :vaara-urakka "Väärä urakka"})

(def kanavan-otsikko
  {:sahkoposti "Sähköposti"
   :sms "Tekstiviesti"
   :ulkoinen_jarjestelma "Ulkoinen järjestelmä"
   :harja "HARJA"})

(def kuittaustyypin-selite
  {:kuittaamaton "Kuittaamaton"
   :vastaanotto "Vastaanotettu"
   :aloitus "Aloitettu"
   :lopetus "Lopetettu"
   :muutos "Muutos"
   :vastaus "Vastaus"
   :vaara-urakka "Väärä urakka"})

(def kuittaustyypin-lyhenne
  {:vastaanotto "VOT"
   :aloitus "ALO"
   :lopetus "LOP"
   :muutos "MUU"
   :vastaus "VAS"
   :vaara-urakka "VAA"})

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

;; Jos muutat näitä, päivitä myös kuittausvaatimukset-str!
(def kuittausvaatimukset
  {;; URK (kysely)
   :kysely {:kuittaustyyppi :lopetus
            :kuittausaika {:talvi (t/hours 72)
                           :kesa (t/hours 72)}}

   ;; TPP (toimenpidepyyntö)
   :toimenpidepyynto {:kuittaustyyppi :vastaanotto
                      :kuittausaika {:talvi (t/minutes 10)
                                     :kesa (t/minutes 10)}}
   ;; TUR (tiedoksi)
   :tiedoitus {:kuittaustyyppi :vastaanotto
               :kuittausaika {:talvi (t/hours 1)
                              ;; TODO: Toteuta erikoistapaus "Seuraavan arkipäivän aamuna klo 9" VHAR-7367
                              :kesa (fn [ilmoitusaika]
                                      )}}})

(def kuittausvaatimukset-str
  ["URK lopetus 72h sisällä."
   "TPP vastaanotto 10min sisällä."
   "TUR vastaanotto talvikaudella 1h sisällä ja kesällä seuraavan arkipäivän aamuna klo 9."])


(defn ilmoitustyypin-lyhenne-ja-nimi
  [tyyppi]
  (str (ilmoitustyypin-lyhenne tyyppi) " (" (ilmoitustyypin-nimi tyyppi) ")"))

(defn nayta-henkilo
  "Palauttaa merkkijonon mallia 'Etunimi Sukunimi, Organisaatio Y1234'"
  [henkilo]
  (when henkilo
    (let [tulos
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
            (:ytunnus henkilo))]
      (when-not (empty? tulos) tulos))))

(defn nayta-henkilon-yhteystiedot
  "Palauttaa merkkijonon mallia 'Etunimi Sukunimi, 55501202, etunimi.sukunimi@example.com, Organisaatio Y1234'"
  [{:keys [etunimi sukunimi organisaatio ytunnus tyopuhelin matkapuhelin sahkoposti] :as henkilo}]
  (when henkilo
    (let [tulos
          (str
            etunimi
            (when (and etunimi sukunimi) " ")
            sukunimi
            (when
              (and
                (or etunimi sukunimi)
                (or organisaatio ytunnus))
              ", ")
            (when (or tyopuhelin matkapuhelin) (str ", " (or tyopuhelin matkapuhelin)))
            (when sahkoposti (str ", " sahkoposti))
            organisaatio
            (when (and ytunnus organisaatio) " ")
            ytunnus)]
      (when-not (empty? tulos) tulos))))

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
  {nil "Kaikki"
   :tyomaajarjestelyihinLiittyvaIlmoitus "Työmaajärjestelyihin liittyvä ilmoitus"
   :kuoppiaTiessa "Kuoppia tiessä"
   :kelikysely "Kelikysely"
   :soratienKuntoHuono "Soratien kunto huono"
   :saveaTiella "Savea tiellä"
   :liikennettaVaarantavaEsteTiella "Liikennettä vaarantava este tiellä"
   :irtokiviaTiella "Irtokiviä tiellä"
   :kevyenLiikenteenVaylaanLiittyvaIlmoitus "Kevyen liikenteen väylä"
   :raivausJaKorjaustoita "Raivaus ja korjaustöitä"
   :auraustarve "Auraustarve"
   :yliauraus "Yliauraus"
   :kaivonKansiRikki "Kaivon kansi rikki"
   :kevyenLiikenteenVaylatOvatLiukkaita "Kevyen liikenteen väylät ovat liukkaita"
   :routaheitto "Routaheitto"
   :avattavatPuomit "Avattavat puomit"
   :tievalaistusVioittunutOnnettomuudessa "Tievalaistus vioittunut onnettomuudessa"
   :muuKyselyTaiNeuvonta "Muu kysely tai neuvonta"
   :soratienTasaustarve "Soratien tasaustarve"
   :tieTaiTienReunaOnPainunut "Tie tai tien reuna on painunut"
   :siltaanLiittyvaIlmoitus "Siltaan liittyvä ilmoitus"
   :polynsidontatarve "Pölynsidontatarve"
   :liikennevalotEivatToimi "Liikennevalot eivät toimi"
   :kunnossapitoJaHoitotyo "Kunnossapito- ja hoitotyö"
   :vettaTiella "Vettä tiellä"
   :aurausvallitNakemaesteena "Aurausvallit näkemäesteenä"
   :ennakoivaVaroitus "Ennakoiva varoitus"
   :levahdysalueeseenLiittyvaIlmoitus "Levähdysalueeseen liittyvä ilmoitus"
   :sohjonPoisto "Sohjon poisto"
   :liikennekeskusKuitannutLoppuneeksi "Liikennekeskus kuitannut loppuneeksi"
   :muuToimenpidetarve "Muu toimenpidetarve"
   :hiekoitustarve "Hiekoitustarve"
   :tietOvatJaatymassa "Tiet ovat jäätymässä"
   :jaatavaaSadetta "Jäätävää sadetta"
   :tienvarsilaitteisiinLiittyvaIlmoitus "Tienvarsilaitteisiin liittyvä ilmoitus"
   :oljyaTiella "Öljyä tiellä"
   :sahkojohtoOnPudonnutTielle "Sähköjohto on pudonnut tielle"
   :tieOnSortunut "Tie on sortunut"
   :tievalaistusVioittunut "Tievalaistus vioittunut"
   :testilahetys "Testilähetys"
   :tievalaistuksenLamppujaPimeana "Tievalaistuksen lamppuja pimeänä"
   :virkaApupyynto "Virka-apupyynto"
   :tiemerkintoihinLiittyvaIlmoitus "Tiemerkintöihin liittyvä ilmoitus"
   :tulvavesiOnNoussutTielle "Tulvavesi on noussut tielle"
   :niittotarve "Niittotarve"
   :kuormaOnLevinnytTielle "Kuorma on levinnyt tielle"
   :tieOnLiukas "Tie on liukas"
   :tiellaOnEste "Tiellä on este"
   :harjaustarve "Harjaustarve"
   :hoylaystarve "Höyläystarve"
   :tietyokysely "Tietyökysely"
   :paallystevaurio "Päällystevaurio"
   :rikkoutunutAjoneuvoTiella "Rikkoutunut ajoneuvo tiellä"
   :mustaaJaataTiella "Mustaa jäätä tiellä"
   :kevyenLiikenteenVaylillaOnLunta "Kevyen liikenteen väylillä on lunta"
   :hirviaitaVaurioitunut "Hirviaita vaurioitunut"
   :korvauskysely "Korvauskysely"
   :puitaOnKaatunutTielle "Puita on kaatunut tielle"
   :rumpuunLiittyvaIlmoitus "Rumpuun liittyva ilmoitus"
   :lasiaTiella "Lasia tiellä"
   :liukkaudentorjuntatarve "Liukkaudentorjuntatarve"
   :alikulkukaytavassaVetta "Alikulkukäytävässä vetta"
   :kevyenliikenteenAlikulkukaytavassaVetta "Kevyenliikenteen alikulkukaytavassa vettä"
   :tievalaistuksenLamppuPimeana "Tievalaistuksen lamppu pimeänä"
   :kevyenLiikenteenVaylatOvatJaisiaJaLiukkaita "Kevyen liikenteen väylät ovat jäisiä ja liukkaita"
   :kuoppa "Kuoppa"
   :toimenpidekysely "Toimenpidekysely"
   :pysakkiinLiittyvaIlmoitus "Pysäkkiin liittyvä ilmoitus"
   :nakemaalueenRaivaustarve "Näkemäalueen raivaustarve"
   :vesakonraivaustarve "Vesakonraivaustarve"
   :muuttuvatOpasteetEivatToimi "Muuttuvat opasteet eivät toimi"
   :tievalaistus "Tievalaistus"
   :vesiSyovyttanytTienReunaa "Vesi syövyttänyt tien reunaa"
   :raskasAjoneuvoJumissa "Raskas ajoneuvo jumissa"
   :myrskyvaurioita "Myrskyvaurioita"
   :kaidevaurio "Kaidevaurio"
   :liikennemerkkeihinLiittyvaIlmoitus "Liikennemerkkeihin liittyvä ilmoitus"
   :siirrettavaAjoneuvo "Siirrettävä ajoneuvo"
   :tielleOnVuotanutNestettaLiikkuvastaAjoneuvosta "Tielle on vuotanut nestettä liikkuvasta ajoneuvosta"
   :tapahtumaOhi "Tapahtuma ohi"
   :kevyenLiikenteenVaylatOvatjaatymassa "Kevyenliikenteen vaylat ovat jäätymässä"
   :tietOvatjaisiaJamarkia "Tiet ovat jäisia ja märkiä"
   :kiertotienKunnossapito "Kiertotien kunnossapito"})

(defn parsi-selitteet [selitteet]
  (string/join ", "
               (mapv #(get +ilmoitusten-selitteet+ %)
                     selitteet)))

(defn virka-apupyynto? [ilmoitus]
  (some #(or (= % :virkaApupyynto) (= % "virkaApupyynto")) (:selitteet ilmoitus)))

(def +ilmoitustilat+ #{:suljetut :avoimet})

(def +kuittauksen-vakiofraasit+
  [ ;;;; Käytetyimmät vakiofraasit ;;;;
   "Ei toimenpidetarvetta, tie laatuvaatimusten mukaisessa kunnossa" ;; ent. Ei toimenpidetarvetta
   "Selvitetään toimenpidetarve"
   "Toimenpiteet alkamassa"
   "Toimenpiteet  käynnissä (myös kiirepäivän TUR-kuittaus)" ;; huom. käytetään myös kiirepäivän TUR-kuittauksina
   "Toimenpiteet päättyneet"
   "Aurataan lumi/sohjo" ;; ent. Aurataan
   "Liukkautta torjutaan" ;; lisätty maaliskuussa 2022
   "Polanne tasataan" ;; ent. Polanne poistetaan
   "Harjataan harjausohjelman mukaisesti" ;; ent. Hiekoitushiekka poistetaan
   "Harjataan (esim. kuran tai lehtien poisto)" ;; huom. Käytetään esim. kuran ja lehtien poistossa
   "Päällyste paikataan hoitokierroksen yhteydessä" ;; ent. Päällyste paikataan
   "Kelirikkokohteen liikennöitävyyttä helpotetaan" ;; ent. unkokelirikkokohde korjataan
   "Soratien pinta tasataan keliolosuhteet huomioiden" ;; ent. Soratien pinta tasataan
   "Pöly sidotaan mahdollisuuksien mukaan" ;; ent. Pöly sidotaan
   "Niitetään niitto-ohjelman mukaan" ;; ent. Niitetään
   "Vesakko raivataan syksyn aikana" ;; ent. Vesakko raivataan
   "Vesakon raivaus ei tämän vuoden raivausohjelmassa" ;; lisätty maaliskuussa 2022
   "Korjataan, ajankohta ei vielä tiedossa (varusteet ja laitteet)" ;; huom. varusteet ja laiteet, lisätty maaliskuussa 2022
   ;;;; Muut vakiofraasit ;;;;
   "Soitetaan tienkäyttäjälle"
   "Tilanne ohi"
   "Palaute vastaanotettu" ;; ent. Viesti vastaanotettu
   "Aurausviitoitus korjataan" ;; lisätty maaliskuussa 2022
   "Jäätyneet rummut aukaistaan"
   "Lumivallit madalletaan"
   "Merkit puhdistetaan"
   "Reunapaalut pestään"
   "Sulamisvesi torjutaan"
   "Ajoratamerkinnät tehdään ohjelman mukaisesti ja keliolosuhteet huomioiden" ;; ent. Ajoratamerkinnät tehdään
   "Hoidetaan liikennöitäväksi"
   "Kaatuneet puut raivataan"
   "Liikenteenohjaus järjestetään"
   "Merkki asennetaan, ajankohta ei vielä tiedossa" ;; ent. Merkki asennetaan
   "Merkki poistetaan, ajankohta ei vielä tiedossa" ;; ent. Merkki poistetaan
   "Merkit uusitaan, ajankohta ei vielä tiedossa" ;; ent. Merkit uusitaan
   "Päällystetyn tien reuna täytetään"
   "Onnettomuuden raivaus tehdään"
   "Roska-astiat tyhjennetään hoitokierroksen yhteydessä" ;; ent. Roska-astiat tyhjennetään
   "Roskat poistetaan hoitokierroksen yhteydessä" ;; ent. Roskat poistetaan
   "Reunakivet korjataan hoitokierroksen yhteydessä" ;; ent. Reunakivet korjataan
   "Routaheitto tasataan hoitokierroksen yhteydessä" ;; ent. Routaheitto tasataan
   "Tie ojitetaan (sivuojat jne)"
   "Tien reunapalteet poistetaan, ajankohta ei vielä tiedossa" ;; ent. Tien reunapalteet poistetaan
])
