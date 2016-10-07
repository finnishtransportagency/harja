(ns harja.palvelin.palvelut.ilmoitukset
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log]
            [clj-time.coerce :refer [from-sql-time]]
            [harja.kyselyt.ilmoitukset :as q]
            [harja.domain.ilmoitukset :as ilmoitukset-domain]
            [harja.palvelin.palvelut.urakat :as urakat]
            [harja.palvelin.integraatiot.tloik.tloik-komponentti :as tloik]
            [clj-time.core :as t]
            [harja.pvm :as pvm]
            [clj-time.coerce :as c]
            [harja.domain.oikeudet :as oikeudet]
            [clojure.java.jdbc :as jdbc])
  (:import (java.util Date)))

(def ilmoitus-xf
  (comp
    (harja.geo/muunna-pg-tulokset :sijainti)
    (map konv/alaviiva->rakenne)
    (map #(konv/string->keyword % :tila))
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

(defn ilmoitus-myohassa? [{:keys [ilmoitustyyppi kuittaukset ilmoitettu]}]
  (let [ilmoitusaika (c/from-sql-time ilmoitettu)
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

(defn- sisaltaa-aloituskuittauksen?
  [ilmoitus]
  (let [{:keys [kuittaukset]} ilmoitus
        aloituskuittaukset (filter
                             #(= (:kuittaustyyppi %) :aloitus)
                             kuittaukset)]
    (> (count aloituskuittaukset) 0)))

(defn- sisaltaa-aloituskuittauksen-aikavalilla?
  [ilmoitus kulunut-aika]
  (let [{:keys [ilmoitettu kuittaukset]} ilmoitus
        ilmoitusaika (c/from-sql-time ilmoitettu)
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

(defn hae-ilmoitukset
  [db user {:keys [hallintayksikko urakka urakoitsija urakkatyyppi tilat tyypit
                   kuittaustyypit aikavali hakuehto selite vain-myohassa?
                   aloituskuittauksen-ajankohta tr-numero
                   ilmoittaja-nimi ilmoittaja-puhelin]}]
  (let [aikavali-alku (when (first aikavali)
                        (konv/sql-date (first aikavali)))
        aikavali-loppu (when (second aikavali)
                         (konv/sql-date (second aikavali)))
        urakat (urakat/kayttajan-urakka-idt-aikavalilta
                 db user oikeudet/ilmoitukset-ilmoitukset
                 urakka urakoitsija urakkatyyppi hallintayksikko
                 (first aikavali) (second aikavali))
        tyypit (mapv name tyypit)
        selite-annettu? (boolean (and selite (first selite)))
        selite (if selite-annettu? (name (first selite)) "")
        tilat (into #{} tilat)
        debug-viesti (str "Haetaan ilmoituksia: "
                          (viesti urakat "urakoista" "ilman urakoita")
                          (viesti aikavali-alku "alkaen" "ilman alkuaikaa")
                          (viesti aikavali-loppu "päättyen" "ilman päättymisaikaa")
                          (viesti tyypit "tyypeistä" "ilman tyyppirajoituksia")
                          (viesti kuittaustyypit "kuittaustyypeistä" "ilman kuittaustyyppirajoituksia")
                          (viesti aloituskuittauksen-ajankohta "aloituskuittausrajaksella: " "ilman aloituskuittausrajausta")
                          (viesti vain-myohassa? "vain myöhässä olevat: " "myös myöhästyneet")
                          (viesti selite "selitteellä:" "ilman selitettä")
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
                                      :alku_annettu (hakuehto-annettu? aikavali-alku)
                                      :loppu_annettu (hakuehto-annettu? aikavali-loppu)
                                      :kuittaamattomat (contains? tilat :kuittaamaton)
                                      :vastaanotetut (contains? tilat :vastaanotettu)
                                      :aloitetut (contains? tilat :aloitettu)
                                      :lopetetut (contains? tilat :lopetettu)
                                      :alku aikavali-alku
                                      :loppu aikavali-loppu
                                      :tyypit_annettu (hakuehto-annettu? tyypit)
                                      :tyypit tyypit
                                      :teksti_annettu (hakuehto-annettu? hakuehto)
                                      :teksti (str "%" hakuehto "%")
                                      :selite_annettu selite-annettu?
                                      :selite selite
                                      :tr-numero tr-numero
                                      :ilmoittaja-nimi (when ilmoittaja-nimi
                                                         (str "%" ilmoittaja-nimi "%"))
                                      :ilmoittaja-puhelin (when ilmoittaja-puhelin
                                                            (str "%" ilmoittaja-puhelin "%"))}))
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
        ilmoitukset (case aloituskuittauksen-ajankohta
                      :alle-tunti (filter #(sisaltaa-aloituskuittauksen-aikavalilla? % (t/hours 1)) ilmoitukset)
                      :myohemmin (filter #(and
                                           (sisaltaa-aloituskuittauksen? %)
                                           (not (sisaltaa-aloituskuittauksen-aikavalilla? % (t/hours 1))))
                                         ilmoitukset)
                      ilmoitukset)]
    (log/debug "Löydettiin ilmoitukset: " (map :id ilmoitukset))
    (log/debug "Jokaisella on kuittauksia " (map #(count (:kuittaukset %)) ilmoitukset) "kappaletta")
    ilmoitukset))

(defn hae-ilmoitus [db user id]
  (let [kayttajan-urakat (urakat/kayttajan-urakka-idt-aikavalilta db user oikeudet/ilmoitukset-ilmoitukset)]
    (first
      (konv/sarakkeet-vektoriin
        (into []
              ilmoitus-xf
              (q/hae-ilmoitus db {:id id
                                  :urakat kayttajan-urakat}))
        {:kuittaus :kuittaukset}))))

(defn tallenna-ilmoitustoimenpide [db tloik _
                                   {:keys [ilmoituksen-id
                                           ulkoinen-ilmoitusid
                                           tyyppi
                                           vapaateksti
                                           vakiofraasi
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

    (vec (remove nil? ilmoitustoimenpiteet))))

(defn hae-ilmoituksia-idlla [db user {:keys [id]}]
  (log/debug "Haetaan päivitetyt tiedot ilmoituksille " (pr-str id))
  (let [id-vektori (if (vector? id) id [id])
        kayttajan-urakat (urakat/kayttajan-urakka-idt-aikavalilta db user oikeudet/ilmoitukset-ilmoitukset)
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
  (let [urakka-idt (q/hae-ilmoituskuittausten-urakat db
                                                 (map
                                                     :ilmoituksen-id ilmoitustoimenpiteet))]
    (doseq [urakka-id urakka-idt]
      (oikeudet/vaadi-kirjoitusoikeus oikeudet/ilmoitukset-ilmoitukset user urakka-id))))

(defn tallenna-ilmoitustoimenpiteet [db tloik user ilmoitustoimenpiteet]
  (tarkista-oikeudet db user ilmoitustoimenpiteet)
  (vec
    (for [ilmoitustoimenpide ilmoitustoimenpiteet]
      (tallenna-ilmoitustoimenpide db tloik user ilmoitustoimenpide))))

(defrecord Ilmoitukset []
  component/Lifecycle
  (start [{db :db
           tloik :tloik
           http :http-palvelin
           :as this}]
    (julkaise-palvelu http :hae-ilmoitukset
                      (fn [user tiedot]
                        (hae-ilmoitukset db user tiedot)))
    (julkaise-palvelu http :hae-ilmoitus
                      (fn [user tiedot]
                        (hae-ilmoitus db user tiedot)))
    (julkaise-palvelu http :tallenna-ilmoitustoimenpiteet
                      (fn [user ilmoitustoimenpiteet]
                        (tallenna-ilmoitustoimenpiteet db tloik user ilmoitustoimenpiteet)))
    (julkaise-palvelu http :hae-ilmoituksia-idlla
                      (fn [user tiedot]
                        (hae-ilmoituksia-idlla db user tiedot)))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :hae-ilmoitukset
                     :tallenna-ilmoitustoimenpiteet
                     :hae-ilmoituksia-idlla)
    this))
