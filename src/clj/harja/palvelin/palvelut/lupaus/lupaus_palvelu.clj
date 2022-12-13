(ns harja.palvelin.palvelut.lupaus.lupaus-palvelu
  (:require [com.stuartsierra.component :as component]
            [harja.id :refer [id-olemassa?]]
            [harja.kyselyt
             [lupaus-kyselyt :as lupaus-kyselyt]
             [urakat :as urakat-q]
             [budjettisuunnittelu :as budjetti-q]
             [valikatselmus :as valikatselmus-q]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.lupaus-domain :as lupaus-domain]
            [harja.domain.roolit :as roolit]
            [harja.domain.urakka :as urakka]
            [harja.domain.kulut.valikatselmus :as valikatselmus-domain]
            [clojure.java.jdbc :as jdbc]
            [clojure.set :as set]
            [harja.kyselyt.konversio :as konversio]
            [harja.kyselyt.kommentit :as kommentit]
            [harja.pvm :as pvm]
            [taoensso.timbre :as log]))

(defn- sitoutumistiedot [lupausrivit]
  {:pisteet (:sitoutuminen-pisteet (first lupausrivit))
   :id (:sitoutuminen-id (first lupausrivit))})

(defn- liita-lupausryhmien-pisteet [lupausrivit]
  (let [ryhmat (group-by :lupausryhma-id lupausrivit)]
    (into {}
          (map (fn [[avain rivit]]
                 (let [pisteet (reduce + 0 (map :pisteet rivit))
                       kyselypisteet (reduce + 0 (map :kyselypisteet rivit))]
                   {avain {:pisteet pisteet
                           :kyselypisteet kyselypisteet
                           :pisteet-max (+ pisteet kyselypisteet)
                           :pisteet-ennuste (lupaus-domain/rivit->ennuste rivit)
                           :pisteet-toteuma (lupaus-domain/rivit->toteuma rivit)
                           :odottaa-kannanottoa (lupaus-domain/lupaus->odottaa-kannanottoa rivit)
                           :merkitsevat-odottaa-kannanottoa (lupaus-domain/lupaus->merkitseva-odottaa-kannanottoa rivit)}}))
               ryhmat))))

(defn- lupausryhman-max-pisteet [max-pisteet ryhma-id]
  (get max-pisteet ryhma-id))

(defn- lupausryhman-tiedot [lupausrivit]
  (let [ryhma-id->lupaukset (group-by :lupausryhma-id lupausrivit)
        ryhmat (map first (vals ryhma-id->lupaukset))
        lupausryhman-pisteet (liita-lupausryhmien-pisteet lupausrivit)]
    (->> ryhmat
         (map #(select-keys % [:lupausryhma-id :lupausryhma-otsikko
                               :lupausryhma-jarjestys :lupausryhma-alkuvuosi]))
         (map #(assoc % :lupaukset (get ryhma-id->lupaukset (:lupausryhma-id %))))
         (map #(set/rename-keys % {:lupausryhma-id :id
                                   :lupausryhma-otsikko :otsikko
                                   :lupausryhma-jarjestys :jarjestys
                                   :lupausryhma-alkuvuosi :alkuvuosi}))
         (map #(assoc % :kirjain (lupaus-domain/numero->kirjain (:jarjestys %))))
         (map #(merge % (lupausryhman-max-pisteet lupausryhman-pisteet (:id %)))))))

(def db-vastaus->speqcl-avaimet
  {:f1 :id
   :f2 :kuukausi
   :f3 :vuosi
   :f4 :vastaus
   :f5 :lupaus-vaihtoehto-id
   :f6 :pisteet
   :f7 :veto-oikeutta-kaytetty
   :f8 :veto-oikeus-aika
   :f9 :paatos})

(defn- maarita-urakan-tavoitehinta
  "Urakalle voidaan budjetoida tavoitehinta hoitokausittain. Päätellään siis hoitokauden järjestysnumero ja tarkistetaan urakka_tavoite taulusta,
  että mikä on kulloisenkin hoitokauden tavoitehinta."
  [db urakka-id hk-alkupvm]
  (let [;; Jotta voidaan päätellä hoitokauden numero, joudutaan hakemaan urakan tietoja
        urakan-tiedot (first (urakat-q/hae-urakka db {:id urakka-id}))
        valitun-hoitokauden-alkuvuosi (pvm/vuosi hk-alkupvm)
        kuluva-hoitokausi-nro (inc (- 5
                                      (- (pvm/vuosi (:loppupvm urakan-tiedot)) valitun-hoitokauden-alkuvuosi)))
        budjetit (budjetti-q/hae-budjettitavoite db {:urakka urakka-id})
        valitun-hoitokauden-budjetti (first (filterv (fn [b]
                                                       (= (:hoitokausi b) kuluva-hoitokausi-nro))
                                                     budjetit))
        tavoitehinta (when valitun-hoitokauden-budjetti (:tarjous-tavoitehinta valitun-hoitokauden-budjetti))]
    tavoitehinta))

(defn- lupauksen-vastausvaihtoehdot [db {:keys [lupaus-id lupaustyyppi] :as lupaus}]
  (when-not (= lupaustyyppi "yksittainen")
    (lupaus-kyselyt/hae-lupaus-vaihtoehdot db {:lupaus-id lupaus-id})))

(defn- liita-lupaus-vaihtoehdot [db lupaus]
  (assoc lupaus :vaihtoehdot (lupauksen-vastausvaihtoehdot db lupaus)))

(defn valikatselmus-tehty-hoitokaudelle?
  "Onko urakalle tehty välikatselmus annetulla hoitokaudella."
  [db urakka-id hoitokauden-alkuvuosi]
  {:pre [(number? urakka-id) (number? hoitokauden-alkuvuosi)]}
  (lupaus-domain/valikatselmus-tehty?
    (valikatselmus-q/hae-urakan-paatokset-hoitovuodelle db urakka-id hoitokauden-alkuvuosi)))

(defn tallennettu-bonus-tai-sanktio [db urakka-id hoitokauden-alkuvuosi]
  (->
    (valikatselmus-q/hae-urakan-paatokset-hoitovuodelle db urakka-id hoitokauden-alkuvuosi)
    lupaus-domain/urakan-paatokset->bonus-tai-sanktio))

(defn lupauspaatos [db urakka-id hoitokauden-alkuvuosi]
  (->
    (valikatselmus-q/hae-urakan-paatokset-hoitovuodelle db urakka-id hoitokauden-alkuvuosi)
    lupaus-domain/urakan-paatokset->lupauspaatos))

(defn valikatselmus-tehty-urakalle? [db urakka-id]
  "Onko urakalle tehty välikatselmus minä tahansa hoitokautena."
  {:pre [(number? urakka-id)]}
  (lupaus-domain/valikatselmus-tehty?
    (valikatselmus-q/hae-urakan-paatokset db {:harja.domain.urakka/id urakka-id})))

(defn hae-urakan-lupaustiedot-hoitokaudelle [db {:keys [urakka-id urakan-alkuvuosi nykyhetki
                                                        valittu-hoitokausi] :as tiedot}]
  (let [[hk-alkupvm hk-loppupvm] valittu-hoitokausi
        vastaus (into []
                      (lupaus-kyselyt/hae-urakan-lupaustiedot db {:urakka urakka-id
                                                               :alkuvuosi urakan-alkuvuosi
                                                               :alkupvm hk-alkupvm
                                                               :loppupvm hk-loppupvm}))
        vastaus (->> vastaus
                     (mapv #(update % :vastaukset konversio/jsonb->clojuremap))
                     (mapv #(update % :vastaukset
                                    (fn [rivit]
                                      (let [tulos (keep
                                                    (fn [r]
                                                      ;; Haku käyttää hakemisessa left joinia, joten on mahdollista, että taulusta
                                                      ;; löytyy nil id
                                                      (when (not (nil? (:f1 r)))
                                                        (clojure.set/rename-keys r db-vastaus->speqcl-avaimet)))
                                                    rivit)]
                                        tulos))))
                     (mapv lupaus-domain/liita-ennuste-tai-toteuma)
                     (mapv #(lupaus-domain/liita-odottaa-kannanottoa % nykyhetki valittu-hoitokausi))
                     (mapv #(lupaus-domain/liita-lupaus-kuukaudet % nykyhetki valittu-hoitokausi))
                     (mapv #(liita-lupaus-vaihtoehdot db %)))
        lupaus-sitoutuminen (sitoutumistiedot vastaus)
        lupausryhmat (lupausryhman-tiedot vastaus)
        piste-maksimi (lupaus-domain/rivit->maksimipisteet lupausryhmat)
        piste-ennuste (lupaus-domain/rivit->ennuste lupausryhmat)
        piste-toteuma (lupaus-domain/rivit->toteuma lupausryhmat)
        odottaa-kannanottoa (lupaus-domain/lupausryhmat->odottaa-kannanottoa lupausryhmat)
        merkitsevat-odottaa-kannanottoa (lupaus-domain/lupausryhmat->merkitsevat-odottaa-kannanottoa lupausryhmat)
        odottaa-urakoitsijan-kannanottoa? (> odottaa-kannanottoa merkitsevat-odottaa-kannanottoa)
        tavoitehinta (when hk-alkupvm (maarita-urakan-tavoitehinta db urakka-id hk-alkupvm))
        tavoitehinta-puuttuu? (not (and tavoitehinta (pos? tavoitehinta)))
        luvatut-pisteet-puuttuu? (not (:pisteet lupaus-sitoutuminen))
        tallennettu-paatos (lupauspaatos db urakka-id (pvm/vuosi hk-alkupvm))
        tallennettu-bonus-tai-sanktio (some-> tallennettu-paatos lupaus-domain/paatos->bonus-tai-sanktio)
        bonus-tai-sanktio (or
                            tallennettu-bonus-tai-sanktio
                            (lupaus-domain/bonus-tai-sanktio
                              {:toteuma (or piste-toteuma piste-ennuste)
                               :lupaus (:pisteet lupaus-sitoutuminen)
                               :tavoitehinta tavoitehinta}))
        ;; Ennuste voidaan tehdä, jos hoitokauden alkupäivä on menneisyydessä ja bonus-tai-sanktio != nil
        ennusteen-voi-tehda? (and (pvm/sama-tai-jalkeen? nykyhetki hk-alkupvm)
                                  bonus-tai-sanktio)
        hoitovuosi-valmis? (boolean piste-toteuma)
        ennusteen-tila (cond tallennettu-bonus-tai-sanktio
                             :katselmoitu-toteuma

                             hoitovuosi-valmis?
                             :alustava-toteuma

                             ennusteen-voi-tehda?
                             :ennuste

                             :else
                             :ei-viela-ennustetta)]
    {:lupaus-sitoutuminen (if tallennettu-paatos
                            ;; Näytetään päätökseen tallennetut pisteet, jos saatavilla
                            {:pisteet (::valikatselmus-domain/lupaus-luvatut-pisteet tallennettu-paatos)}
                            lupaus-sitoutuminen)
     :lupausryhmat lupausryhmat
     ;; Lähtötiedot tarkistusta varten, ei välttämätöntä
     :lahtotiedot {:urakka-id urakka-id
                   :urakan-alkuvuosi urakan-alkuvuosi
                   :valittu-hoitokausi valittu-hoitokausi
                   :nykyhetki nykyhetki} ; Minkä hetken mukaan on laskettu
     ;; Yhteenveto
     :yhteenveto {:ennusteen-tila ennusteen-tila
                  :pisteet {:maksimi piste-maksimi
                            :ennuste piste-ennuste
                            :toteuma (or
                                       ;; Näytetään päätökseen tallennetut pisteet, jos saatavilla
                                       (::valikatselmus-domain/lupaus-toteutuneet-pisteet tallennettu-paatos)
                                       piste-toteuma)}
                  :bonus-tai-sanktio bonus-tai-sanktio
                  :tavoitehinta (or
                                  ;; Näytetään päätökseen tallennettu tavoitehinta, jos saatavilla
                                  (::valikatselmus-domain/lupaus-tavoitehinta tallennettu-paatos)
                                  tavoitehinta)
                  :odottaa-kannanottoa odottaa-kannanottoa
                  :merkitsevat-odottaa-kannanottoa merkitsevat-odottaa-kannanottoa
                  :odottaa-urakoitsijan-kannanottoa? odottaa-urakoitsijan-kannanottoa?
                  :valikatselmus-tehty-urakalle? (valikatselmus-tehty-urakalle? db urakka-id)
                  :tavoitehinta-puuttuu? tavoitehinta-puuttuu?
                  :luvatut-pisteet-puuttuu? luvatut-pisteet-puuttuu?}}))

(defn- hae-urakan-lupaustiedot [db user {:keys [urakka-id urakan-alkuvuosi valittu-hoitokausi] :as tiedot}]
  {:pre [(number? urakka-id) (number? urakan-alkuvuosi) valittu-hoitokausi
         (inst? (first valittu-hoitokausi)) (inst? (second valittu-hoitokausi))]}
  (log/debug "hae-urakan-lupaustiedot " tiedot)
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-valitavoitteet user urakka-id)
  (hae-urakan-lupaustiedot-hoitokaudelle db tiedot))

(defn vaadi-lupaus-sitoutuminen-kuuluu-urakkaan
  "Tarkistaa, että lupaus-sitoutuminen kuuluu annettuun urakkaan"
  [db urakka-id lupaus-sitoutuminen-id]
  (when (id-olemassa? lupaus-sitoutuminen-id)
    (let [lupauksen-urakka (:urakka-id (first (lupaus-kyselyt/hae-lupauksen-urakkatieto db {:id lupaus-sitoutuminen-id})))]
      (when-not (= lupauksen-urakka urakka-id)
        (throw (SecurityException. (str "Lupaus " lupaus-sitoutuminen-id " ei kuulu valittuun urakkaan "
                                        urakka-id " vaan urakkaan " lupauksen-urakka)))))))

(defn tallenna-urakan-luvatut-pisteet
  [db user {:keys [id urakka-id pisteet] :as tiedot}]
  (log/debug "tallenna-urakan-luvatut-pisteet tiedot " tiedot)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-valitavoitteet user urakka-id)
  (when (not (roolit/tilaajan-kayttaja? user))
    (throw (SecurityException. "Luvattujen pisteiden tallentaminen vaatii tilaajan käyttäjän.")))
  (when id
    (vaadi-lupaus-sitoutuminen-kuuluu-urakkaan db urakka-id id))
  (assert (not (valikatselmus-tehty-urakalle? db urakka-id))
          "Luvattuja pisteitä ei voi enää muuttaa, jos urakalle on tehty välikatselmus.")
  (jdbc/with-db-transaction [db db]
                            (let [params {:id id
                                          :urakka-id urakka-id
                                          :pisteet pisteet
                                          :kayttaja (:id user)}]
                              (if id
                                (lupaus-kyselyt/paivita-urakan-luvatut-pisteet<! db params)
                                (lupaus-kyselyt/lisaa-urakan-luvatut-pisteet<! db params)))))

(defn- paivita-lupaus-vastaus [db user-id {:keys [id vastaus lupaus-vaihtoehto-id]}]
  {:pre [db user-id id]}
  (let [paivitetyt-rivit (lupaus-kyselyt/paivita-lupaus-vastaus!
                           db
                           {:vastaus vastaus
                            :lupaus-vaihtoehto-id lupaus-vaihtoehto-id
                            :muokkaaja user-id
                            :id id})]
    (assert (pos? paivitetyt-rivit) (str "lupaus_vastaus id " id " ei löytynyt"))
    (first (lupaus-kyselyt/hae-lupaus-vastaus db {:id id}))))

(defn- lisaa-lupaus-vastaus [db user-id {:keys [lupaus-id urakka-id kuukausi vuosi paatos vastaus lupaus-vaihtoehto-id]}]
  {:pre [db user-id lupaus-id urakka-id kuukausi vuosi (boolean? paatos)]}
  (lupaus-kyselyt/lisaa-lupaus-vastaus<!
    db
    {:lupaus-id lupaus-id
     :urakka-id urakka-id
     :kuukausi kuukausi
     :vuosi vuosi
     :paatos paatos
     :vastaus vastaus
     :lupaus-vaihtoehto-id lupaus-vaihtoehto-id
     :luoja user-id}))

(defn- sallittu-vaihtoehto?
  "Tarkista, että lupaus-vaihtoehto viittaa oikeaan lupaukseen."
  [db lupaus-id lupaus-vaihtoehto-id]
  (if lupaus-vaihtoehto-id
    (let [sallittu? (some-> (lupaus-kyselyt/hae-lupaus-vaihtoehto db {:id lupaus-vaihtoehto-id})
                            first
                            :lupaus-id
                            (= lupaus-id))]
      (log/debug "sallittu-vaihtoehto?" sallittu? lupaus-id lupaus-vaihtoehto-id)
      sallittu?)
    ;; Sallitaan nil-arvon asettaminen.
    true))

(defn- tarkista-vastaus-ja-vaihtoehto
  "Tarkista, että 'yksittainen'-tyyppiselle lupaukselle on annettu boolean 'vastaus',
  ja muun tyyppiselle sallittu 'lupaus-vaihtoehto-id'.
  vastaus / lupaus-vaihtoehto-id voidaan asettaa nil-arvoon."
  [db lupaus vastaus lupaus-vaihtoehto-id]
  (cond (= "yksittainen" (:lupaustyyppi lupaus))
        (assert (nil? lupaus-vaihtoehto-id))

        (or (= "monivalinta" (:lupaustyyppi lupaus))
            (= "kysely" (:lupaustyyppi lupaus)))
        (do (assert (nil? vastaus))
            (assert (sallittu-vaihtoehto? db (:id lupaus) lupaus-vaihtoehto-id)))))

(defn- tarkista-lupaus-vastaus
  [db user {:keys [id lupaus-id urakka-id kuukausi vuosi paatos vastaus lupaus-vaihtoehto-id] :as tiedot}]
  {:pre [db user tiedot]}
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-valitavoitteet user urakka-id)
  (when (and paatos (not (roolit/tilaajan-kayttaja? user)))
    (throw (SecurityException. "Lopullisen päätöksen tekeminen vaatii tilaajan käyttäjän.")))
  (assert (not (and vastaus lupaus-vaihtoehto-id)))
  ;; HUOM: vastaus/lupaus-vaihtoehto-id saa päivittää nil-arvoon (= ei vastattu)
  (let [{:keys [lupaus-id urakka-id vuosi kuukausi]} (if id
                                                       (first (lupaus-kyselyt/hae-lupaus-vastaus db {:id id}))
                                                       tiedot)
        _ (assert lupaus-id)
        lupaus (first (lupaus-kyselyt/hae-lupaus db {:id lupaus-id}))]
    (assert (false? (valikatselmus-tehty-hoitokaudelle?
                       db urakka-id (pvm/hoitokauden-alkuvuosi vuosi kuukausi)))
            "Vastauksia ei voi enää muuttaa välikatselmuksen jälkeen")
    ;; Tarkista, että "yksittainen"-tyyppiselle lupaukselle on annettu boolean "vastaus",
    ;; ja muun tyyppiselle sallittu "lupaus-vaihtoehto-id".
    (tarkista-vastaus-ja-vaihtoehto db lupaus vastaus lupaus-vaihtoehto-id)
    (when-not id
      ;; Tarkista, että kirjaus/päätös tulee sallitulle kuukaudelle.
      (assert (lupaus-domain/sallittu-kuukausi? lupaus kuukausi paatos)))))

(defn- nykyhetki
  "Mahdollistaa nykyhetken lähettämisen parametrina kehitysympäristössä.
  Tämän tarkoitus on helpottaa testaamista.
  Tuotantoympäristössä palauttaa aina todellisen nykyhetken."
  [{:keys [nykyhetki] :as _tiedot} {:keys [kehitysmoodi] :as _asetukset}]
  (or (and kehitysmoodi nykyhetki)
      (pvm/nyt)))

(defn- lisaa-nykyhetki [tiedot asetukset]
  (assoc tiedot :nykyhetki (nykyhetki tiedot asetukset)))

(defn- vastaa-lupaukseen
  [db user {:keys [id lupaus-id urakka-id kuukausi vuosi paatos vastaus lupaus-vaihtoehto-id] :as tiedot}]
  {:pre [db user tiedot]}
  (log/debug "vastaa-lupaukseen " tiedot)
  (tarkista-lupaus-vastaus db user tiedot)
  (jdbc/with-db-transaction [db db]
                            (if id
                              (paivita-lupaus-vastaus db (:id user) tiedot)
                              (lisaa-lupaus-vastaus db (:id user) tiedot))))

(defn- kommentit
  [db user {:keys [lupaus-id urakka-id aikavali] :as tiedot}]
  {:pre [db user tiedot (number? lupaus-id) (number? urakka-id)
         (inst? (first aikavali)) (inst? (second aikavali))]}
  (log/debug "kommentit" tiedot)
  (let [[alkupvm loppupvm] aikavali]
    (oikeudet/vaadi-lukuoikeus oikeudet/urakat-valitavoitteet user urakka-id)
    (lupaus-kyselyt/kommentit db {:lupaus-id lupaus-id
                               :urakka-id urakka-id
                               :vuosi-alku (pvm/vuosi alkupvm)
                               :kuukausi-alku (pvm/kuukausi alkupvm)
                               :vuosi-loppu (pvm/vuosi loppupvm)
                               :kuukausi-loppu (pvm/kuukausi loppupvm)})))

(defn- lisaa-kommentti
  [db user {:keys [lupaus-id urakka-id kuukausi vuosi kommentti] :as tiedot}]
  {:pre [db user tiedot (number? lupaus-id) (number? urakka-id) (number? kuukausi) (number? vuosi)
         (string? kommentti)]}
  (log/debug "lisaa-kommentti" tiedot)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-valitavoitteet user urakka-id)
  (jdbc/with-db-transaction [db db]
                            (let [kommentti (kommentit/luo-kommentti<!
                                              db nil kommentti nil (:id user))
                                  lupaus-kommentti (lupaus-kyselyt/lisaa-lupaus-kommentti<!
                                                     db
                                                     {:lupaus-id lupaus-id
                                                      :urakka-id urakka-id
                                                      :kuukausi kuukausi
                                                      :vuosi vuosi
                                                      :kommentti-id (:id kommentti)})]
                              (merge kommentti lupaus-kommentti))))

(defn- poista-kommentti
  [db user {:keys [id] :as tiedot}]
  {:pre [db user tiedot (number? id) (number? (:id user))]}
  (log/debug "poista-kommentti" tiedot)
  ;; Kysely poistaa vain käyttäjän itse luomia kommentteja, joten muita tarkistuksia ei ole.
  (let [paivitetyt-rivit (lupaus-kyselyt/poista-kayttajan-oma-kommentti!
                           db
                           {:id id
                            :kayttaja (:id user)})]
    (when-not (= paivitetyt-rivit 1)
      (throw (SecurityException. "Kommentin poistaminen epäonnistui")))
    paivitetyt-rivit))

(defn- tallenna-kuukausittaiset-pisteet
  "Vuonna 2019/2020 alkaneet urakat eivät käytä lupauksia, vaan heille aluevastaava tallentaa ennustetut pisteet tai
  toteutuneet pisteet kuukausittain. Näiden pisteiden perusteella voidaan sitten laskea bonus/sanktio."
  [db user {:keys [urakka-id kuukausi vuosi pisteet tyyppi id] :as tiedot}]
  {:pre [db user tiedot (number? urakka-id) (number? kuukausi) (number? vuosi) (number? pisteet) (string? tyyppi)
         (number? (:id user))]}
  (log/debug "tallenna-kuukausittaiset-pisteet :: tiedot" tiedot)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-valitavoitteet user urakka-id)
  ;; Syyskuuuhun saa vastata vain tilaajan käyttäjä
  (when (and (= 9 kuukausi) (not (roolit/tilaajan-kayttaja? user)))
    (throw (SecurityException. "Lopullisen päätöksen tekeminen vaatii tilaajan käyttäjän.")))
  (let [;; Varmistetaan, että annetun urakan alkuvuosi on 2019 tai 2020.
        urakan-tiedot (first (urakat-q/hae-urakka db {:id urakka-id}))
        urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
        _ (assert (lupaus-domain/vuosi-19-20? urakan-alkuvuosi)
                  "Kuukausittaiset pisteet sallittu vain urakoille, jotka ovat alkaneet 2019/2020")
        arvot {:urakka-id urakka-id
               :kuukausi kuukausi
               :vuosi vuosi
               :pisteet pisteet
               :tyyppi tyyppi
               :kayttaja (:id user)}]
    (if id
      (lupaus-kyselyt/paivita-kuukausittaiset-pisteet<! db (merge arvot {:id id}))
      (lupaus-kyselyt/tallenna-kuukausittaiset-pisteet<! db arvot))))

(defn- poista-kuukausittaiset-pisteet
  "Poistetaan jo syötetyt pisteet"
  [db user {:keys [urakka-id id] :as tiedot}]
  {:pre [db user tiedot (number? urakka-id) (number? id) (number? (:id user))]}
  (log/debug "poista-kuukausittaiset-pisteet :: tiedot" tiedot)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-valitavoitteet user urakka-id)

  (let [;; Varmistetaan, että annetun urakan alkuvuosi on 2019 tai 2020.
        urakan-tiedot (first (urakat-q/hae-urakka db {:id urakka-id}))
        urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
        _ (assert (lupaus-domain/vuosi-19-20? urakan-alkuvuosi)
                  "Kuukausittaiset pisteet sallittu vain urakoille, jotka ovat alkaneet 2019/2020")
        arvot {:urakka-id urakka-id
               :id id}
        ;; Haetaan kuukausivastauksen tiedot
        kuukausivastaus (lupaus-kyselyt/hae-kuukausivastaus db {:id id})
        ;; Syyskuuuhun saa vastata vain tilaajan käyttäjä
        _ (when (and (= 9 (:kuukausi kuukausivastaus)) (not (roolit/tilaajan-kayttaja? user)))
          (throw (SecurityException. "Lopullisen päätöksen tekeminen vaatii tilaajan käyttäjän.")))]
    (lupaus-kyselyt/poista-kuukausittaiset-pisteet<! db arvot)))

(defn hae-kuukausittaiset-pisteet-hoitokaudelle
  "Kuukausittaiset pisteet haetaan 2019/2020 alkaville urakoille. Näillä ei ole varsinaisia lupauksia ollenkaan"
  ([db tiedot]
   (hae-kuukausittaiset-pisteet-hoitokaudelle db {} tiedot))
  ([db kayttaja {:keys [urakka-id valittu-hoitokausi nykyhetki] :as tiedot}]
   {:pre [db tiedot (number? urakka-id) (not (nil? valittu-hoitokausi))]}
   (log/debug "hae-kuukausittaiset-pisteet-hoitokaudelle :: tiedot" tiedot)
   (let [hk-alkupvm (first valittu-hoitokausi)
         vuosi (pvm/vuosi hk-alkupvm)
         urakan-tiedot (first (urakat-q/hae-urakka db {:id urakka-id}))
         _ (assert
             (lupaus-domain/urakka-19-20? urakan-tiedot)
             "Kuukausittaiset pisteet sallittu vain urakoille, jotka ovat alkaneet 2019/2020")
         kuukausipisteet (lupaus-kyselyt/hae-kuukausittaiset-pisteet db {:hk-alkuvuosi vuosi
                                                                         :urakka-id urakka-id})
         sitoutumistiedot (first (lupaus-kyselyt/hae-sitoutumistiedot db {:hk-alkuvuosi vuosi
                                                                          :urakka-id urakka-id}))
         valikatselmus-tehty-hoitokaudelle? (valikatselmus-tehty-hoitokaudelle? db urakka-id (pvm/vuosi hk-alkupvm))
         lopulliset-pisteet (lupaus-domain/kokoa-vastauspisteet kayttaja kuukausipisteet urakka-id
                              valittu-hoitokausi valikatselmus-tehty-hoitokaudelle?
                              nykyhetki)
         tavoitehinta (when hk-alkupvm (maarita-urakan-tavoitehinta db urakka-id hk-alkupvm))
         ;; Haetaan annetuista ennusteista viimeinen, jossa on arvo
         ennuste-pisteet (last (keep #(when (:pisteet %)
                                        (:pisteet %))
                                 ;; Lasketaan kuukaudet 10,11,12,1-8 mukaan ennustepisteisiin eli skipataan viimeinen, koska syyskuu on toteuma
                                 (take 11 lopulliset-pisteet)))
         toteuma-pisteet (:pisteet (last lopulliset-pisteet))
         bonus-tai-sanktio (lupaus-domain/bonus-tai-sanktio {:toteuma (or toteuma-pisteet ennuste-pisteet)
                                                             :lupaus (:pisteet sitoutumistiedot)
                                                             :tavoitehinta tavoitehinta})
         ;; Näille -19/-20 alkaneille MH-urakoille (muita ei voi tällä funktiolla käsitellä) lasketaan
         ;; Indeksikorjaus automaattisesti hintaan mukaan
         bonus-tai-sanktio-pvm (-> (second valittu-hoitokausi)
                                 (pvm/vuosi)
                                 (pvm/luo-pvm-dec-kk 9 15)
                                 (konversio/sql-date))
         indeksikorotus-parametrit {:pvm bonus-tai-sanktio-pvm
                                    :indeksi (:indeksi urakan-tiedot)
                                    :maara (if (:sanktio bonus-tai-sanktio)
                                             (:sanktio bonus-tai-sanktio)
                                             (:bonus bonus-tai-sanktio))
                                    :urakka-id urakka-id
                                    :sanktiolaji (if (:sanktio bonus-tai-sanktio) "lupaussanktio" nil)}
         indeksikorotus (:korotus (first (lupaus-kyselyt/hae-indeksikorotus-summalle db indeksikorotus-parametrit)))
         luvatut-pisteet-puuttuu? (not (:pisteet sitoutumistiedot))
         hoitovuosi-valmis? (boolean toteuma-pisteet)
         ;; Ennuste voidaan tehdä, jos hoitokauden alkupäivä on menneisyydessä ja bonus-tai-sanktio != nil
         ennusteen-voi-tehda? (and (pvm/sama-tai-jalkeen? nykyhetki hk-alkupvm)
                                bonus-tai-sanktio)
         tavoitehinta-puuttuu? (not (and tavoitehinta (pos? tavoitehinta)))
         ;; Tarvitseeko urakoitsijalle lähettää muistutussähköpostia?
         odottaa-urakoitsijan-kannanottoa? (and
                                             (lupaus-domain/odottaa-urakoitsijan-kannanottoa?
                                                  lopulliset-pisteet)
                                             ;; Lähetetään sähköposti vanhalle urakalle vasta
                                             ;; sitten kun aluevastaava on täyttänyt luvatut
                                             ;; pisteet (merkki siitä, että lupaukset on otettu
                                             ;; käyttöön urakalle).
                                             (:pisteet sitoutumistiedot))]
     {:lupaus-sitoutuminen sitoutumistiedot
      :kuukausipisteet lopulliset-pisteet
      :yhteenveto {:ennusteen-tila (cond
                                     valikatselmus-tehty-hoitokaudelle? :katselmoitu-toteuma
                                     hoitovuosi-valmis? :alustava-toteuma
                                     ennusteen-voi-tehda? :ennuste
                                     :else :ei-viela-ennustetta)
                   :pisteet {:maksimi 100
                             :ennuste ennuste-pisteet
                             :toteuma toteuma-pisteet}
                   :bonus-tai-sanktio bonus-tai-sanktio
                   :indeksikorotus indeksikorotus
                   :tavoitehinta tavoitehinta
                   :odottaa-urakoitsijan-kannanottoa? odottaa-urakoitsijan-kannanottoa?
                   :valikatselmus-tehty-urakalle? valikatselmus-tehty-hoitokaudelle?
                   :tavoitehinta-puuttuu? tavoitehinta-puuttuu?
                   :luvatut-pisteet-puuttuu? luvatut-pisteet-puuttuu?}})))

(defn hae-kuukausittaiset-pisteet [db user {:keys [urakka-id valittu-hoitokausi nykyhetki] :as tiedot}]
  {:pre [db user tiedot (number? urakka-id) (not (nil? valittu-hoitokausi)) (number? (:id user))]}
  (log/debug "hae-kuukausittaiset-pisteet :: tiedot" tiedot)
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-valitavoitteet user urakka-id)
  (hae-kuukausittaiset-pisteet-hoitokaudelle db user tiedot))

(defrecord Lupaus [asetukset]
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
                      :hae-urakan-lupaustiedot
                      (fn [user tiedot]
                        (hae-urakan-lupaustiedot
                          (:db this)
                          user
                          (lisaa-nykyhetki tiedot asetukset))))

    (julkaise-palvelu (:http-palvelin this)
                      :tallenna-luvatut-pisteet
                      (fn [user tiedot]
                        (tallenna-urakan-luvatut-pisteet
                          (:db this)
                          user
                          (lisaa-nykyhetki tiedot asetukset))))

    (julkaise-palvelu (:http-palvelin this)
                      :vastaa-lupaukseen
                      (fn [user tiedot]
                        (vastaa-lupaukseen (:db this) user tiedot)))

    (julkaise-palvelu (:http-palvelin this)
                      :lupauksen-kommentit
                      (fn [user tiedot]
                        (kommentit (:db this) user tiedot)))

    (julkaise-palvelu (:http-palvelin this)
                      :lisaa-lupauksen-kommentti
                      (fn [user tiedot]
                        (lisaa-kommentti (:db this) user tiedot)))

    (julkaise-palvelu (:http-palvelin this)
                      :poista-lupauksen-kommentti
                      (fn [user tiedot]
                        (poista-kommentti (:db this) user tiedot)))

    (julkaise-palvelu (:http-palvelin this)
                      :tallenna-kuukausittaiset-pisteet
                      (fn [user tiedot]
                        (tallenna-kuukausittaiset-pisteet (:db this) user (lisaa-nykyhetki tiedot asetukset))))

    (julkaise-palvelu (:http-palvelin this)
                      :poista-kuukausittaiset-pisteet
                      (fn [user tiedot]
                        (poista-kuukausittaiset-pisteet (:db this) user (lisaa-nykyhetki tiedot asetukset))))

    (julkaise-palvelu (:http-palvelin this)
                      :hae-kuukausittaiset-pisteet
                      (fn [user tiedot]
                        (hae-kuukausittaiset-pisteet (:db this) user (lisaa-nykyhetki tiedot asetukset))))

    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :hae-urakan-lupaustiedot
                     :tallenna-luvatut-pisteet
                     :vastaa-lupaukseen
                     :lupauksen-kommentit
                     :lisaa-lupauksen-kommentti
                     :poista-lupauksen-kommentti
                     :tallenna-kuukausittaiset-pisteet
                     :poista-kuukausittaiset-pisteet
                     :hae-kuukausittaiset-pisteet)
    this))
