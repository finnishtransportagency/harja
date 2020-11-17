(ns harja.palvelin.palvelut.ilmoitukset
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin
             :refer [julkaise-palvelu poista-palvelut async]]
            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log]
            [clj-time.coerce :refer [from-sql-time]]
            [harja.kyselyt.tieliikenneilmoitukset :as q]
            [harja.domain.tieliikenneilmoitukset :as ilmoitukset-domain]
            [harja.palvelin.palvelut.urakat :as urakat]
            [harja.kyselyt.urakat :as ur-q]
            [harja.palvelin.integraatiot.tloik.tloik-komponentti :as tloik]
            [clj-time.core :as t]
            [harja.pvm :as pvm]
            [clj-time.coerce :as c]
            [harja.domain.oikeudet :as oikeudet]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [harja.palvelin.palvelut.kayttajatiedot :as kayttajatiedot])
  (:import (java.util Date)))

(def ilmoitus-xf
  (comp
    (harja.geo/muunna-pg-tulokset :sijainti)
    (map konv/alaviiva->rakenne)
    (map #(konv/string->keyword % :tila))
    (map #(konv/string->keyword % [:kuittaus :suunta]))
    (map #(konv/string->keyword % [:kuittaus :kanava]))
    (map #(assoc % :urakkatyyppi (keyword (:urakkatyyppi %))))
    (map #(konv/array->vec % :selitteet))
    (map #(assoc % :selitteet (mapv keyword (:selitteet %))))
    (map #(assoc-in % [:kuittaus :kuittaustyyppi] (keyword (get-in % [:kuittaus :kuittaustyyppi]))))
    (map #(assoc % :ilmoitustyyppi (keyword (:ilmoitustyyppi %))))
    (map #(assoc-in % [:ilmoittaja :tyyppi] (keyword (get-in % [:ilmoittaja :tyyppi]))))))

(defn hakuehto-annettu? [p]
  (cond
    (number? p) true
    (instance? Date p) true
    (keyword? p) true
    (instance? Boolean p) true
    (map? p) (some true? (map #(hakuehto-annettu? (val %)) p))
    (empty? p) false
    :else true))

(defn- viesti [mille mista ilman]
  (str ", "
       (if (hakuehto-annettu? mille)
         (str mista " " (pr-str mille))
         (str ilman))))

(defn ilmoitus-myohassa? [{:keys [ilmoitustyyppi kuittaukset valitetty-urakkaan]}]
  (let [ilmoitusaika (c/from-sql-time valitetty-urakkaan)
        vaadittu-kuittaustyyppi (get-in ilmoitukset-domain/kuittausvaatimukset [ilmoitustyyppi :kuittaustyyppi])
        vaadittu-kuittausaika (get-in ilmoitukset-domain/kuittausvaatimukset [ilmoitustyyppi :kuittausaika])
        vaadittu-aika-kulunut? (t/after? (t/now) (t/plus ilmoitusaika vaadittu-kuittausaika))
        vaaditut-kuittaukset (filter
                               (fn [kuittaus]
                                 (and
                                   (pvm/valissa?
                                     (c/from-sql-time (:kuitattu kuittaus))
                                     ilmoitusaika
                                     (t/plus ilmoitusaika vaadittu-kuittausaika)
                                     false)
                                   (= (:kuittaustyyppi kuittaus) vaadittu-kuittaustyyppi)))
                               kuittaukset)
        myohassa? (and vaadittu-aika-kulunut?
                       (empty? vaaditut-kuittaukset))]
    myohassa?))

(defn- lisaa-tieto-myohastymisesta [ilmoitus]
  (assoc ilmoitus :myohassa? (ilmoitus-myohassa? ilmoitus)))

(defn- suodata-myohastyneet [ilmoitukset]
  (filter
    #(true? (:myohassa? %))
    ilmoitukset))

(defn- suodata-toimenpiteita-aiheuttaneet [ilmoitukset]
  (filter
    #(true? (:aiheutti-toimenpiteita %))
    ilmoitukset))

(defn- sisaltaa-aloituskuittauksen?
  [ilmoitus]
  (let [{:keys [kuittaukset]} ilmoitus
        aloituskuittaukset (filter
                             #(= (:kuittaustyyppi %) :aloitus)
                             kuittaukset)]
    (> (count aloituskuittaukset) 0)))

(defn- sisaltaa-aloituskuittauksen-aikavalilla?
  [ilmoitus kulunut-aika]
  (let [{:keys [valitetty-urakkaan kuittaukset]} ilmoitus
        ilmoitusaika (c/from-sql-time valitetty-urakkaan)
        aloituskuittaukset (filter
                             #(= (:kuittaustyyppi %) :aloitus)
                             kuittaukset)
        aloituskuittauksia-annetuna-ajan-valissa
        (true? (some
                 (fn [kuittaus]
                   (pvm/valissa?
                     (c/from-sql-time (:kuitattu kuittaus))
                     ilmoitusaika
                     (t/plus ilmoitusaika kulunut-aika)
                     false))
                 aloituskuittaukset))]
    aloituskuittauksia-annetuna-ajan-valissa))

(defn aikavaliehto [hakuehdot vakioaikavali-avain alkuaika-avain loppuaika-avain]
  (let [vakioaikavali (get hakuehdot vakioaikavali-avain)
        alkuaika (get hakuehdot alkuaika-avain)
        loppuaika (get hakuehdot loppuaika-avain)]
    (if-let [tunteja (:tunteja vakioaikavali)]
      [(c/to-date (pvm/tuntia-sitten tunteja)) (pvm/nyt)]
      [alkuaika loppuaika])))

(defn hae-ilmoitukset
  ([db user suodattimet] (hae-ilmoitukset db user suodattimet nil))
  ([db user {:keys [hallintayksikko urakka urakoitsija urakkatyyppi tilat tyypit
                    kuittaustyypit hakuehto selite
                    aloituskuittauksen-ajankohta tr-numero tunniste
                    ilmoittaja-nimi ilmoittaja-puhelin vaikutukset] :as hakuehdot}
    max-maara]
   (let [valitetty-urakkaan-aikavali (or (:aikavali hakuehdot) (aikavaliehto hakuehdot :valitetty-urakkaan-vakioaikavali :valitetty-urakkaan-alkuaika :valitetty-urakkaan-loppuaika))
         valitetty-urakkaan-aikavali-alku (when (first valitetty-urakkaan-aikavali)
                                    (konv/sql-timestamp (first valitetty-urakkaan-aikavali)))
         valitetty-urakkaan-aikavali-loppu (when (second valitetty-urakkaan-aikavali)
                                     (konv/sql-timestamp (second valitetty-urakkaan-aikavali)))
         toimenpiteet-aloitettu-aikavali (aikavaliehto hakuehdot :toimenpiteet-aloitettu-vakioaikavali :toimenpiteet-aloitettu-alkuaika :toimenpiteet-aloitettu-loppuaika)
         toimenpiteet-aloitettu-aikavali-alku (when (first toimenpiteet-aloitettu-aikavali)
                                                (konv/sql-timestamp (first toimenpiteet-aloitettu-aikavali)))
         toimenpiteet-aloitettu-aikavali-loppu (when (second toimenpiteet-aloitettu-aikavali)
                                                 (konv/sql-timestamp (second toimenpiteet-aloitettu-aikavali)))
         urakat (kayttajatiedot/kayttajan-urakka-idt-aikavalilta
                  db user (fn [urakka-id kayttaja]
                            (oikeudet/voi-lukea? oikeudet/ilmoitukset-ilmoitukset
                                                 urakka-id
                                                 kayttaja))
                  urakka urakoitsija
                  (case urakkatyyppi
                    :kaikki nil
                    ; Ao. vesivayla-käsittely estää kantapoikkeuksen. Jos väylävalitsin tulee, tämä voidaan ehkä poistaa
                    :vesivayla :vesivayla-hoito
                    urakkatyyppi)
                  hallintayksikko
                  (first valitetty-urakkaan-aikavali) (second valitetty-urakkaan-aikavali))
         _ (oikeudet/merkitse-oikeustarkistus-tehdyksi!)
         tyypit (mapv name tyypit)
         selite-annettu? (boolean (and selite (first selite)))
         selite (if selite-annettu? (name (first selite)) "")
         tilat (into #{} tilat)
         vain-myohassa? (contains? vaikutukset :myohassa)
         vain-toimenpiteita-aiheuttaneet? (contains? vaikutukset :aiheutti-toimenpiteita)
         debug-viesti (str "Haetaan ilmoituksia: "
                           (viesti urakat "urakoista" "ilman urakoita")
                           (viesti valitetty-urakkaan-aikavali-alku "alkaen" "ilman alkuaikaa")
                           (viesti valitetty-urakkaan-aikavali-loppu "päättyen" "ilman päättymisaikaa")
                           (viesti toimenpiteet-aloitettu-aikavali-alku "toimenpiteet alkaen" "ilman toimenpiteiden alkuaikaa")
                           (viesti toimenpiteet-aloitettu-aikavali-loppu "toimenpiteet päättyen" "ilman toimenpiteiden päättymisaikaa")
                           (viesti tyypit "tyypeistä" "ilman tyyppirajoituksia")
                           (viesti kuittaustyypit "kuittaustyypeistä" "ilman kuittaustyyppirajoituksia")
                           (viesti aloituskuittauksen-ajankohta "aloituskuittausrajaksella: " "ilman aloituskuittausrajausta")
                           (viesti vain-myohassa? "vain myöhässä olevat: " "myös myöhästyneet")
                           (viesti vain-toimenpiteita-aiheuttaneet? "vain toimenpiteitä aiheuttaneet olevat: " "myös toimenpiteitä aiheuttamattomat")
                           (viesti selite "selitteellä:" "ilman selitettä")
                           (viesti tunniste "tunnisteella:" "ilman tunnistetta")
                           (viesti hakuehto "hakusanoilla:" "ilman tekstihakua")
                           (viesti tr-numero "tienumerolla:" "ilman tienumeroa")
                           (cond
                             (:avoimet tilat) ", mutta vain avoimet."
                             (and (:suljetut tilat) (:avoimet tilat)) ", ja näistä avoimet JA suljetut."
                             (:suljetut tilat) ", ainoastaan suljetut."))
         _ (log/debug debug-viesti)
         ilmoitukset
         (when-not (empty? urakat)
           (konv/sarakkeet-vektoriin
             (into []
                   ilmoitus-xf
                   (q/hae-ilmoitukset db
                                      {:urakat urakat
                                       :alku_annettu (hakuehto-annettu? valitetty-urakkaan-aikavali-alku)
                                       :loppu_annettu (hakuehto-annettu? valitetty-urakkaan-aikavali-loppu)
                                       :toimenpiteet_alku_annettu (hakuehto-annettu? toimenpiteet-aloitettu-aikavali-alku)
                                       :toimenpiteet_loppu_annettu (hakuehto-annettu? toimenpiteet-aloitettu-aikavali-loppu)
                                       :kuittaamattomat (contains? tilat :kuittaamaton)
                                       :vastaanotetut (contains? tilat :vastaanotettu)
                                       :aloitetut (contains? tilat :aloitettu)
                                       :lopetetut (contains? tilat :lopetettu)
                                       :alku valitetty-urakkaan-aikavali-alku
                                       :loppu valitetty-urakkaan-aikavali-loppu
                                       :toimenpiteet_alku toimenpiteet-aloitettu-aikavali-alku
                                       :toimenpiteet_loppu toimenpiteet-aloitettu-aikavali-loppu
                                       :tyypit_annettu (hakuehto-annettu? tyypit)
                                       :tyypit tyypit
                                       :teksti_annettu (hakuehto-annettu? hakuehto)
                                       :teksti (str "%" hakuehto "%")
                                       :selite_annettu selite-annettu?
                                       :selite selite
                                       :tunniste_annettu (hakuehto-annettu? tunniste)
                                       :tunniste (when-not (str/blank? tunniste)
                                                   (str "%" tunniste "%"))
                                       :tr-numero tr-numero
                                       :ilmoittaja-nimi (when-not (str/blank? ilmoittaja-nimi)
                                                          (str "%" ilmoittaja-nimi "%"))
                                       :ilmoittaja-puhelin (when-not (str/blank? ilmoittaja-puhelin)
                                                             (str "%" ilmoittaja-puhelin "%"))
                                       :max-maara max-maara}))
             {:kuittaus :kuittaukset}))
         ilmoitukset (mapv
                       #(-> %
                            (assoc :uusinkuittaus
                                   (when-not (empty? (:kuittaukset %))
                                     (:kuitattu (last (sort-by :kuitattu (:kuittaukset %))))))
                            (lisaa-tieto-myohastymisesta))
                       ilmoitukset)
         ilmoitukset (if vain-myohassa?
                       (suodata-myohastyneet ilmoitukset)
                       ilmoitukset)
         ilmoitukset (if vain-toimenpiteita-aiheuttaneet?
                       (suodata-toimenpiteita-aiheuttaneet ilmoitukset)
                       ilmoitukset)
         ilmoitukset (case aloituskuittauksen-ajankohta
                       :alle-tunti (filter #(sisaltaa-aloituskuittauksen-aikavalilla? % (t/hours 1)) ilmoitukset)
                       :myohemmin (filter #(and
                                             (sisaltaa-aloituskuittauksen? %)
                                             (not (sisaltaa-aloituskuittauksen-aikavalilla? % (t/hours 1))))
                                          ilmoitukset)
                       ilmoitukset)]
     (log/debug "Löydettiin ilmoitukset: " (mapv :id ilmoitukset))
     (log/debug "Jokaisella on kuittauksia " (mapv #(count (:kuittaukset %)) ilmoitukset) "kappaletta")
     ilmoitukset)))

(defn hae-ilmoitukset-raportille
  "Palauttaa ilmoitukset raporttia varten, minimaalisella tietosisällöllä ja ilman hidastavaa sorttausta."
  [db user {:keys [hallintayksikko urakka urakoitsija urakkatyyppi aikavali]}]
  (let [aikavali-alku (when (first aikavali)
                        (konv/sql-timestamp (first aikavali)))
        aikavali-loppu (when (second aikavali)
                         (konv/sql-timestamp (second aikavali)))
        urakat (cond urakka
                     [urakka]

                     hallintayksikko
                     (map :id (ur-q/hae-hallintayksikon-urakat db hallintayksikko))

                     :default ;; Kaikki urakat
                     nil)
        debug-viesti (str "Haetaan ilmoituksia raportille: "
                          (viesti urakat "urakoista" "ilman urakoita")
                          (viesti urakat "urakoista" "ilman urakoita")
                          (viesti aikavali-alku "alkaen" "ilman alkuaikaa")
                          (viesti aikavali-loppu "päättyen" "ilman päättymisaikaa"))
        _ (log/debug debug-viesti)
        _ (log/debug "HAKU PARAMS: " {:urakat_annettu (boolean urakat)
                                      :urakat urakat
                                      :urakkatyyppi_annettu (some? urakkatyyppi)
                                      :urakkatyyppi (when urakkatyyppi (name urakkatyyppi))
                                      :alku_annettu (hakuehto-annettu? aikavali-alku)
                                      :loppu_annettu (hakuehto-annettu? aikavali-loppu)
                                      :alku aikavali-alku
                                      :loppu aikavali-loppu})
        ilmoitukset
        (into []
              ilmoitus-xf
              (q/hae-ilmoitukset-raportille db
                                            {:urakat_annettu (boolean urakat)
                                             :urakat urakat
                                             :urakkatyyppi_annettu (and (some? urakkatyyppi)
                                                                        (not= urakkatyyppi :kaikki))
                                             :urakkatyyppi (when urakkatyyppi (name urakkatyyppi))
                                             :alku_annettu (hakuehto-annettu? aikavali-alku)
                                             :loppu_annettu (hakuehto-annettu? aikavali-loppu)
                                             :alku aikavali-alku
                                             :loppu aikavali-loppu}))]
    ilmoitukset))

(defn hae-ilmoitus [db user id]
  (let [tulos (first
                (konv/sarakkeet-vektoriin
                  (into []
                        ilmoitus-xf
                        (q/hae-ilmoitus db {:id id}))
                  {:kuittaus :kuittaukset}))]
    (oikeudet/vaadi-lukuoikeus oikeudet/ilmoitukset-ilmoitukset user (:urakka tulos))
    tulos))

(defn tallenna-ilmoitustoimenpide [db tloik _
                                   {:keys [ilmoituksen-id
                                           ulkoinen-ilmoitusid
                                           tyyppi
                                           vapaateksti
                                           vakiofraasi
                                           aiheutti-toimenpiteita
                                           ilmoittaja-etunimi
                                           ilmoittaja-sukunimi
                                           ilmoittaja-matkapuhelin
                                           ilmoittaja-tyopuhelin
                                           ilmoittaja-sahkoposti
                                           ilmoittaja-organisaatio
                                           ilmoittaja-ytunnus
                                           kasittelija-etunimi
                                           kasittelija-sukunimi
                                           kasittelija-matkapuhelin
                                           kasittelija-tyopuhelin
                                           kasittelija-sahkoposti
                                           kasittelija-organisaatio
                                           kasittelija-ytunnus]
                                    :as ilmoitustoimenpide}]
  (log/debug (format "Tallennetaan uusi ilmoitustoimenpide: %s" ilmoitustoimenpide))
  (let [tallenna (fn [tyyppi vapaateksti vakiofraasi]
                   (let
                     [toimenpide (jdbc/with-db-transaction [db db]
                                   (q/luo-ilmoitustoimenpide<!
                                     db
                                     {:ilmoitus ilmoituksen-id
                                      :ilmoitusid ulkoinen-ilmoitusid
                                      :kuitattu (harja.pvm/nyt)
                                      :vakiofraasi vakiofraasi
                                      :vapaateksti vapaateksti
                                      :kuittaustyyppi tyyppi
                                      :tila (when (= tyyppi "valitys") "lahetetty")
                                      :suunta "sisaan"
                                      :kanava "harja"
                                      :kuittaaja_henkilo_etunimi ilmoittaja-etunimi
                                      :kuittaaja_henkilo_sukunimi ilmoittaja-sukunimi
                                      :kuittaaja_henkilo_matkapuhelin ilmoittaja-matkapuhelin
                                      :kuittaaja_henkilo_tyopuhelin ilmoittaja-tyopuhelin
                                      :kuittaaja_henkilo_sahkoposti ilmoittaja-sahkoposti
                                      :kuittaaja_organisaatio_nimi ilmoittaja-organisaatio
                                      :kuittaaja_organisaatio_ytunnus ilmoittaja-ytunnus
                                      :kasittelija_henkilo_etunimi kasittelija-etunimi
                                      :kasittelija_henkilo_sukunimi kasittelija-sukunimi
                                      :kasittelija_henkilo_matkapuhelin kasittelija-matkapuhelin
                                      :kasittelija_henkilo_tyopuhelin kasittelija-tyopuhelin
                                      :kasittelija_henkilo_sahkoposti kasittelija-sahkoposti
                                      :kasittelija_organisaatio_nimi kasittelija-organisaatio
                                      :kasittelija_organisaatio_ytunnus kasittelija-ytunnus}))]

                     (-> toimenpide
                         (assoc :tila (keyword (:tila toimenpide)))
                         (assoc :suunta (keyword (:suunta toimenpide)))
                         (assoc :kanava (keyword (:kanava toimenpide)))
                         (assoc :kuittaustyyppi (keyword (:kuittaustyyppi toimenpide)))
                         (assoc-in [:kuittaaja :etunimi] (:kuittaaja_henkilo_etunimi toimenpide))
                         (assoc-in [:kuittaaja :sukunimi] (:kuittaaja_henkilo_sukunimi toimenpide))
                         (assoc-in [:kuittaaja :matkapuhelin] (:kuittaaja_henkilo_matkapuhelin toimenpide))
                         (assoc-in [:kuittaaja :tyopuhelin] (:kuittaaja_henkilo_tyopuhelin toimenpide))
                         (assoc-in [:kuittaaja :sahkoposti] (:kuittaaja_henkilo_sahkoposti toimenpide))
                         (assoc-in [:kuittaaja :organisaatio] (:kuittaaja_organisaatio_nimi toimenpide))
                         (assoc-in [:kuittaaja :ytunnus] (:kuittaaja_organisaatio_ytunnus toimenpide))
                         (assoc-in [:ilmoittaja :etunimi] (:ilmoittaja_henkilo_etunimi toimenpide))
                         (assoc-in [:ilmoittaja :sukunimi] (:ilmoittaja_henkilo_sukunimi toimenpide))
                         (assoc-in [:ilmoittaja :matkapuhelin] (:ilmoittaja_henkilo_matkapuhelin toimenpide))
                         (assoc-in [:ilmoittaja :tyopuhelin] (:ilmoittaja_henkilo_tyopuhelin toimenpide))
                         (assoc-in [:ilmoittaja :sahkoposti] (:ilmoittaja_henkilo_sahkoposti toimenpide))
                         (assoc-in [:ilmoittaja :organisaatio] (:ilmoittaja_organisaatio_nimi toimenpide))
                         (assoc-in [:ilmoittaja :ytunnus] (:ilmoittaja_organisaatio_ytunnus toimenpide))
                         (assoc-in [:kasittelija :etunimi] (:kasittelija_henkilo_etunimi toimenpide))
                         (assoc-in [:kasittelija :sukunimi] (:kasittelija_henkilo_sukunimi toimenpide))
                         (assoc-in [:kasittelija :matkapuhelin] (:kasittelija_henkilo_matkapuhelin toimenpide))
                         (assoc-in [:kasittelija :tyopuhelin] (:kasittelija_henkilo_tyopuhelin toimenpide))
                         (assoc-in [:kasittelija :sahkoposti] (:kasittelija_henkilo_sahkoposti toimenpide))
                         (assoc-in [:kasittelija :organisaatio] (:kasittelija_organisaatio_nimi toimenpide))
                         (assoc-in [:kasittelija :ytunnus] (:kasittelija_organisaatio_ytunnus toimenpide)))))

        ilmoitustoimenpiteet [(when (and (= tyyppi :aloitus)
                                         (not (q/ilmoitukselle-olemassa-vastaanottokuittaus? db ulkoinen-ilmoitusid)))
                                (let [aloitus-kuittaus (tallenna "vastaanotto" "Vastaanotettu" nil)]
                                  (when tloik
                                    (tloik/laheta-ilmoitustoimenpide tloik (:id aloitus-kuittaus)))
                                  aloitus-kuittaus))

                              (let [kuittaus (tallenna (name tyyppi) vapaateksti vakiofraasi)]
                                (when tloik
                                  (tloik/laheta-ilmoitustoimenpide tloik (:id kuittaus)))
                                kuittaus)]]

    (when (= tyyppi :lopetus)
      (q/ilmoitus-aiheutti-toimenpiteita! db (true? aiheutti-toimenpiteita) ilmoituksen-id))
    (vec (remove nil? ilmoitustoimenpiteet))))

(defn hae-ilmoituksia-idlla [db user {:keys [id]}]
  (log/debug "Haetaan päivitetyt tiedot ilmoituksille " (pr-str id))
  (let [id-vektori (if (vector? id) id [id])
        kayttajan-urakat (kayttajatiedot/kayttajan-urakka-idt-aikavalilta
                           db
                           user
                           (fn [urakka-id kayttaja]
                             (oikeudet/voi-lukea? oikeudet/ilmoitukset-ilmoitukset
                                                  urakka-id
                                                  kayttaja)))
        tiedot (q/hae-ilmoitukset-idlla db id-vektori)
        tulos (konv/sarakkeet-vektoriin
                (into []
                      (comp
                        (filter #(or (nil? (:urakka %)) (kayttajan-urakat (:urakka %))))
                        (harja.geo/muunna-pg-tulokset :sijainti)
                        (map konv/alaviiva->rakenne)
                        (map #(konv/string->keyword % :tila))
                        (map #(konv/array->vec % :selitteet))
                        (map #(assoc % :selitteet (mapv keyword (:selitteet %))))
                        (map #(assoc-in % [:kuittaus :kuittaustyyppi] (keyword (get-in % [:kuittaus :kuittaustyyppi]))))
                        (map #(assoc % :ilmoitustyyppi (keyword (:ilmoitustyyppi %))))
                        (map #(assoc-in % [:ilmoittaja :tyyppi] (keyword (get-in % [:ilmoittaja :tyyppi])))))
                      tiedot)
                {:kuittaus :kuittaukset})]
    (log/debug "Löydettiin tiedot " (count tulos) " ilmoitukselle.")
    tulos))

(defn- tarkista-oikeudet [db user ilmoitustoimenpiteet]
  (let [urakka-idt (mapv :urakka
                         (q/hae-ilmoituskuittausten-urakat db
                                                           (map
                                                             :ilmoituksen-id ilmoitustoimenpiteet)))]
    (doseq [urakka-id urakka-idt]
      (oikeudet/vaadi-kirjoitusoikeus oikeudet/ilmoitukset-ilmoitukset user urakka-id))))

(defn tallenna-ilmoitustoimenpiteet [db tloik user ilmoitustoimenpiteet]
  (vec
    (for [ilmoitustoimenpide ilmoitustoimenpiteet]
      (tallenna-ilmoitustoimenpide db tloik user ilmoitustoimenpide))))

(defn tallenna-ilmoituksen-toimenpiteiden-aloitus [db user idt peruutettu?]
  (if peruutettu?
    (q/peruuta-ilmoitusten-toimenpiteiden-aloitukset! db idt)
    (q/tallenna-ilmoitusten-toimenpiteiden-aloitukset! db idt)))

(defrecord Ilmoitukset []
  component/Lifecycle
  (start [{db :db
           tloik :tloik
           http :http-palvelin
           :as this}]
    (julkaise-palvelu http :hae-ilmoitukset
                      (fn [user tiedot]
                        (hae-ilmoitukset db user tiedot 501)))
    (julkaise-palvelu http :hae-ilmoitus
                      (fn [user tiedot]
                        (hae-ilmoitus db user tiedot)))
    (julkaise-palvelu http :tallenna-ilmoitustoimenpiteet
                      (fn [user ilmoitustoimenpiteet]
                        (tarkista-oikeudet db user ilmoitustoimenpiteet)
                        (async
                          (tallenna-ilmoitustoimenpiteet db tloik user ilmoitustoimenpiteet))))
    (julkaise-palvelu http :hae-ilmoituksia-idlla
                      (fn [user tiedot]
                        (hae-ilmoituksia-idlla db user tiedot)))
    (julkaise-palvelu http :tallenna-ilmoituksen-toimenpiteiden-aloitus
                      (fn [user idt]
                        (tallenna-ilmoituksen-toimenpiteiden-aloitus db user idt false)))
    (julkaise-palvelu http :peruuta-ilmoituksen-toimenpiteiden-aloitus
                      (fn [user idt]
                        (tallenna-ilmoituksen-toimenpiteiden-aloitus db user idt true)))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :hae-ilmoitukset
                     :hae-ilmoitus
                     :tallenna-ilmoitustoimenpiteet
                     :hae-ilmoituksia-idlla
                     :tallenna-ilmoituksen-toimenpiteiden-aloitus
                     :peruuta-ilmoituksen-toimenpiteiden-aloitus)
    this))
