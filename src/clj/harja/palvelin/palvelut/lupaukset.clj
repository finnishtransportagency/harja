(ns harja.palvelin.palvelut.lupaukset
  "Palvelu välitavoitteiden hakemiseksi ja tallentamiseksi."
  (:require [com.stuartsierra.component :as component]
            [harja.id :refer [id-olemassa?]]
            [harja.kyselyt
             [lupaukset :as lupaukset-q]
             [urakat :as urakat-q]
             [budjettisuunnittelu :as budjetti-q]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.konversio :as konv]
            [clojure.java.jdbc :as jdbc]
            [clojure.set :as set]
            [harja.kyselyt.konversio :as konversio]
            [harja.domain.roolit :as roolit]
            [harja.kyselyt.kommentit :as kommentit]
            [harja.pvm :as pvm]))


(defn- sitoutumistiedot [lupausrivit]
  {:pisteet (:sitoutuminen-pisteet (first lupausrivit))
   :id (:sitoutuminen-id (first lupausrivit))})

(defn- numero->kirjain [numero]
  (case numero
    1 "A"
    2 "B"
    3 "C"
    4 "D"
    5 "E"
    nil))

(defn hylatyt [vastaukset]
  (filter #(false? (:vastaus %)) vastaukset))

(defn hyvaksytyt [vastaukset]
  (filter #(true? (:vastaus %)) vastaukset))

(defn hylatty? [vastaukset joustovara-kkta]
  (> (count (hylatyt vastaukset)) joustovara-kkta))

(defn hyvaksytty? [vastaukset joustovara-kkta paatos-kk]
  (let [vastaus-kuukaudet-lkm (if (= paatos-kk 0)
                                12                          ; 0 = kaikki
                                1)
        vaaditut-hyvaksynnat (- vastaus-kuukaudet-lkm joustovara-kkta)
        hyvaksynnat (count (hyvaksytyt vastaukset))]
    (>= hyvaksynnat vaaditut-hyvaksynnat)))

(defn paatokset [vastaukset]
  (filter :paatos vastaukset))

(defn paatos-hyvaksytty? [vastaukset joustovara-kkta paatos-kk]
  (hyvaksytty? (paatokset vastaukset) joustovara-kkta paatos-kk))

(defn paatos-hylatty? [vastaukset joustovara-kkta]
  (hylatty? (paatokset vastaukset) joustovara-kkta))

(defn viimeisin-vastaus [vastaukset]
  (last (sort-by (fn [{:keys [vuosi kuukausi]}]
                   [vuosi kuukausi])
                 vastaukset)))

(defn yksittainen->toteuma [{:keys [vastaukset joustovara-kkta pisteet paatos-kk]}]
  (cond (paatos-hylatty? vastaukset joustovara-kkta)
        0

        (paatos-hyvaksytty? vastaukset joustovara-kkta paatos-kk)
        pisteet

        :else
        nil))

(defn monivalinta->toteuma [{:keys [vastaukset]}]
  (when-let [paatos (viimeisin-vastaus (paatokset vastaukset))]
    (:pisteet paatos)))

(defn kysely->toteuma [lupaus]
  (monivalinta->toteuma lupaus))

(defn yksittainen->ennuste [{:keys [vastaukset joustovara-kkta pisteet]}]
  (if (hylatty? vastaukset joustovara-kkta)
    0
    pisteet))

(defn kysely->ennuste [{:keys [vastaukset kyselypisteet]}]
  (or (:pisteet (viimeisin-vastaus vastaukset))
      kyselypisteet))

(defn monivalinta->ennuste [{:keys [vastaukset pisteet]}]
  (or (:pisteet (viimeisin-vastaus vastaukset))
      pisteet))

(defn lupaus->ennuste [{:keys [lupaustyyppi] :as lupaus}]
  (case lupaustyyppi
    "yksittainen" (yksittainen->ennuste lupaus)
    "kysely" (kysely->ennuste lupaus)
    "monivalinta" (monivalinta->ennuste lupaus)))

(defn lupaus->toteuma [{:keys [lupaustyyppi] :as lupaus}]
  (case lupaustyyppi
    "yksittainen" (yksittainen->toteuma lupaus)
    "kysely" (monivalinta->toteuma lupaus)
    "monivalinta" (monivalinta->toteuma lupaus)))

(defn lupaus->ennuste-tai-toteuma [lupaus]
  (or (when-let [toteuma (lupaus->toteuma lupaus)]
        {:pisteet-toteuma toteuma
         ;; Jos päättävät kuukaudet on täytetty, niin ennuste == toteuma.
         ;; Liitetään sama luku ennuste-avaimen alle, niin on helpompi laskea ryhmäkohtainen ennuste,
         ;; jos ryhmässä on sekaisin ennustetta ja toteumaa.
         :pisteet-ennuste toteuma})
      (when-let [ennuste (lupaus->ennuste lupaus)]
        {:pisteet-ennuste ennuste})))

(defn liita-ennuste-tai-toteuma [lupaus]
  (-> lupaus
      (merge (lupaus->ennuste-tai-toteuma lupaus))))

(defn- rivit->summa
  "Jos jokaisella rivillä on numero annetun avaimen alla, palauta numeroiden summa.
  Muuten palauta nil."
  [rivit avain]
  (let [luvut (->> rivit
                   (map avain)
                   (filter number?))]
    (if (= (count luvut) (count rivit))
      (reduce + luvut)
      nil)))

(defn- ryhma->ennuste [rivit]
  (rivit->summa rivit :pisteet-ennuste))

(defn- ryhma->toteuma [rivit]
  (rivit->summa rivit :pisteet-toteuma))

(defn- liita-lupausryhmien-pisteet [lupausrivit]
  (let [ryhmat (group-by :lupausryhma-id lupausrivit)]
    (into {}
          (map (fn [[avain rivit]]
                 (let [pisteet (reduce + 0 (map :pisteet rivit))
                       kyselypisteet (reduce + 0 (map :kyselypisteet rivit))]
                   {avain {:pisteet pisteet
                           :kyselypisteet kyselypisteet
                           :pisteet-max (+ pisteet kyselypisteet)
                           :pisteet-ennuste (ryhma->ennuste rivit)
                           :pisteet-toteuma (ryhma->toteuma rivit)}}))
               ryhmat))))

(defn- lupausryhman-max-pisteet [max-pisteet ryhma-id]
  (get max-pisteet ryhma-id))

(defn- lupausryhman-tiedot [lupausrivit]
  (let [ryhmat (map first (vals (group-by :lupausryhma-id lupausrivit)))
        max-pisteet (liita-lupausryhmien-pisteet lupausrivit)]
    (->> ryhmat
         (map #(select-keys % [:lupausryhma-id :lupausryhma-otsikko
                               :lupausryhma-jarjestys :lupausryhma-alkuvuosi]))
         (map #(set/rename-keys % {:lupausryhma-id :id
                                   :lupausryhma-otsikko :otsikko
                                   :lupausryhma-jarjestys :jarjestys
                                   :lupausryhma-alkuvuosi :alkuvuosi}))
         (map #(assoc % :kirjain (numero->kirjain (:jarjestys %))))
         (map #(assoc % :odottaa-kannanottoa (rand-int 5)))
         (map #(merge % (lupausryhman-max-pisteet max-pisteet (:id %)))))))

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

(def db-vaihtoehdot->speqcl-avaimet
  {:f1 :pisteet
   :f2 :vaihtoehto
   :f3 :id})

(def ennusteiden-tilat
  #{;; "Ei vielä ennustetta"
    ;; Ensimmäiset ennusteet annetaan Marraskuun alussa, kun tiedot on syötetty ensimmäiseltä  kuukaudelta.
    :ei-viela-ennustetta

    ;; "Huhtikuun ennusteen mukaan urakalle on tulossa Bonusta 5200 €"
    ;; Lopulliset bonukset ja sanktiot sovitaan välikatselmuksessa.
    :ennuste

    ;; "Toteuman mukaan urakalle on tulossa Bonusta 5200 €"
    ;; Lopulliset bonukset ja sanktiot sovitaan välikatselmuksessa.
    :alustava-toteuma

    ;; "Urakalle tuli bonusta 1. hoitovuotena 5200 €"
    ;; Tiedot on käyty läpi välikatselmuksessa.
    :katselmoitu-toteuma})

(defn- maarita-urakan-tavoitehinta
  "Urakalle voidaan budjetoida tavoitehinta hoitokausittain. Päätellään siis hoitokauden järjestysnumero ja tarkistetaan urakka_tavoite taulusta,
  että mikä on kulloisenkin hoitokauden tavoitehinta."
  [db urakka-id hk-alkupvm]
  (let [urakan-tiedot (first (urakat-q/hae-urakka db {:id urakka-id}))
        valitun-hoitokauden-alkuvuosi (pvm/vuosi hk-alkupvm)
        kuluva-hoitokausi-nro (inc (- 5
                                      (- (pvm/vuosi (:loppupvm urakan-tiedot)) valitun-hoitokauden-alkuvuosi)))
        budjetit (budjetti-q/hae-budjettitavoite db {:urakka urakka-id})
        valitun-hoitokauden-budjetti (first (filterv (fn [b]
                                                       (do
                                                         (println "filter budjetit :: b" (pr-str b))
                                                         (= (:hoitokausi b) kuluva-hoitokausi-nro)))
                                                     budjetit))
        tavoitehinta (when valitun-hoitokauden-budjetti (:tavoitehinta valitun-hoitokauden-budjetti))]
    tavoitehinta))

(defn- hae-urakan-lupaustiedot [db user {:keys [urakka-id urakan-alkuvuosi nykyhetki
                                                valittu-hoitokausi] :as tiedot}]
  {:pre [(number? urakka-id) (number? urakan-alkuvuosi)]}
  (println "hae-urakan-lupaustiedot " tiedot)
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-valitavoitteet user urakka-id)
  (let [[hk-alkupvm hk-loppupvm] valittu-hoitokausi
        vastaus (into []
                      (lupaukset-q/hae-urakan-lupaustiedot db {:urakka urakka-id
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
                     (mapv #(update % :vastaus-vaihtoehdot konversio/jsonb->clojuremap))
                     (mapv #(update % :vastaus-vaihtoehdot
                                    (fn [rivit]
                                      (keep
                                        (fn [r]
                                          (when (not (nil? (:f1 r)))
                                            (clojure.set/rename-keys r db-vaihtoehdot->speqcl-avaimet)))
                                        rivit))))
                     (mapv liita-ennuste-tai-toteuma))
        lupaukset (group-by :lupausryhma-otsikko vastaus)
        lupausryhmat (lupausryhman-tiedot vastaus)
        ;; TODO
        ennusteen-voi-tehda? true
        hoitovuosi-valmis? false
        valikatselmus-tehty? false
        ennusteen-tila (cond valikatselmus-tehty? :katselmoitu-toteuma
                             hoitovuosi-valmis? :alustava-toteuma
                             ennusteen-voi-tehda? :ennuste
                             :else :ei-viela-ennustetta)
        piste-maksimi (rivit->summa lupausryhmat :pisteet-max)
        piste-ennuste (rivit->summa lupausryhmat :pisteet-ennuste)
        piste-toteuma (rivit->summa lupausryhmat :piste-toteuma)
        ;; Jotta voidaan päätellä hoitokauden numero, joudutaan hakemaan urakan tietoja
        tavoitehinta (when hk-alkupvm (maarita-urakan-tavoitehinta db urakka-id hk-alkupvm))]
    {:lupaus-sitoutuminen (sitoutumistiedot vastaus)
     :lupausryhmat lupausryhmat
     :lupaukset lupaukset
     ;; TODO
     ;; Lähtötiedot tarkistusta varten, ei välttämätöntä
     :lahtotiedot {:urakka-id urakka-id
                   :urakan-alkuvuosi urakan-alkuvuosi
                   :valittu-hoitokausi valittu-hoitokausi
                   :nykyhetki nykyhetki}    ; Minkä hetken mukaan on laskettu
     ;; Yhteenveto
     :yhteenveto {:ennusteen-tila ennusteen-tila
                  :pisteet {:maksimi piste-maksimi
                            :ennuste piste-ennuste
                            :toteuma piste-toteuma}
                  :bonus-tai-sanktio {;; Joko bonus positiivisena, tai sanktio negatiivisena lukuna
                                      ;:bonus 5200.00
                                      :sanktio -13200.00}
                  :tavoitehinta tavoitehinta}}))

(defn- lupauksen-vastausvaihtoehdot [db user {:keys [lupaus-id] :as tiedot}]
  (lupaukset-q/hae-lupaus-vaihtoehdot db {:lupaus-id lupaus-id}))

(defn vaadi-lupaus-sitoutuminen-kuuluu-urakkaan
  "Tarkistaa, että lupaus-sitoutuminen kuuluu annettuun urakkaan"
  [db urakka-id lupaus-sitoutuminen-id]
  (when (id-olemassa? lupaus-sitoutuminen-id)
    (let [lupauksen-urakka (:urakka-id (first (lupaukset-q/hae-lupauksen-urakkatieto db {:id lupaus-sitoutuminen-id})))]
      (when-not (= lupauksen-urakka urakka-id)
        (throw (SecurityException. (str "Lupaus " lupaus-sitoutuminen-id " ei kuulu valittuun urakkaan "
                                        urakka-id " vaan urakkaan " lupauksen-urakka)))))))

(defn- tallenna-urakan-luvatut-pisteet
  [db user {:keys [id urakka-id pisteet] :as tiedot}]
  (println "tallenna-urakan-luvatut-pisteet tiedot " tiedot)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-valitavoitteet user urakka-id)
  (when id
    (vaadi-lupaus-sitoutuminen-kuuluu-urakkaan db urakka-id id))
  (jdbc/with-db-transaction [db db]
    (let [params {:id id
                  :urakka-id urakka-id
                  :pisteet pisteet
                  :kayttaja (:id user)}
          _vastaus (if id
                     (lupaukset-q/paivita-urakan-luvatut-pisteet<! db params)
                     (lupaukset-q/lisaa-urakan-luvatut-pisteet<! db params))]
      (hae-urakan-lupaustiedot db user tiedot))))

(defn- paivita-lupaus-vastaus [db user-id {:keys [id vastaus lupaus-vaihtoehto-id]}]
  {:pre [db user-id id]}
  (let [paivitetyt-rivit (lupaukset-q/paivita-lupaus-vastaus!
                           db
                           {:vastaus vastaus
                            :lupaus-vaihtoehto-id lupaus-vaihtoehto-id
                            :muokkaaja user-id
                            :id id})]
    (assert (pos? paivitetyt-rivit) (str "lupaus_vastaus id " id " ei löytynyt"))
    (first (lupaukset-q/hae-lupaus-vastaus db {:id id}))))

(defn- lisaa-lupaus-vastaus [db user-id {:keys [lupaus-id urakka-id kuukausi vuosi paatos vastaus lupaus-vaihtoehto-id]}]
  {:pre [db user-id lupaus-id urakka-id kuukausi vuosi (boolean? paatos)]}
  (lupaukset-q/lisaa-lupaus-vastaus<!
    db
    {:lupaus-id lupaus-id
     :urakka-id urakka-id
     :kuukausi kuukausi
     :vuosi vuosi
     :paatos paatos
     :vastaus vastaus
     :lupaus-vaihtoehto-id lupaus-vaihtoehto-id
     :luoja user-id}))

(defn- sallittu-kuukausi? [{:keys [kirjaus-kkt paatos-kk] :as lupaus} kuukausi paatos]
  {:pre [lupaus kuukausi (boolean? paatos)]}
  (let [sallittu? (if paatos
                    (or (= paatos-kk kuukausi)
                        ;; 0 = kaikki
                        (= paatos-kk 0))
                    (contains? (set kirjaus-kkt) kuukausi))]
    (println "sallittu-kuukausi?" sallittu? "kirjaus-kkt" kirjaus-kkt "paatos-kk" paatos-kk "kuukausi" kuukausi "paatos" paatos)
    sallittu?))

(defn- sallittu-vaihtoehto?
  "Tarkista, että lupaus-vaihtoehto viittaa oikeaan lupaukseen."
  [db lupaus-id lupaus-vaihtoehto-id]
  (if lupaus-vaihtoehto-id
    (let [sallittu? (some-> (lupaukset-q/hae-lupaus-vaihtoehto db {:id lupaus-vaihtoehto-id})
                            first
                            :lupaus-id
                            (= lupaus-id))]
      (println "sallittu-vaihtoehto?" sallittu? lupaus-id lupaus-vaihtoehto-id)
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
  (let [lupaus-vastaus (if id
                         (first (lupaukset-q/hae-lupaus-vastaus db {:id id}))
                         tiedot)
        lupaus-id (:lupaus-id lupaus-vastaus)
        _ (assert lupaus-id)
        lupaus (first (lupaukset-q/hae-lupaus db {:id lupaus-id}))]
    ;; Tarkista, että "yksittainen"-tyyppiselle lupaukselle on annettu boolean "vastaus",
    ;; ja muun tyyppiselle sallittu "lupaus-vaihtoehto-id".
    (tarkista-vastaus-ja-vaihtoehto db lupaus vastaus lupaus-vaihtoehto-id)
    (when-not id
      ;; Tarkista, että kirjaus/päätös tulee sallitulle kuukaudelle.
      (assert (sallittu-kuukausi? lupaus kuukausi paatos)))))

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
  (println "vastaa-lupaukseen " tiedot)
  (tarkista-lupaus-vastaus db user tiedot)
  (jdbc/with-db-transaction [db db]
                            (if id
                              (paivita-lupaus-vastaus db (:id user) tiedot)
                              (lisaa-lupaus-vastaus db (:id user) tiedot))))

(defn- kommentit
  [db user {:keys [lupaus-id urakka-id kuukausi vuosi] :as tiedot}]
  {:pre [db user tiedot (number? lupaus-id) (number? urakka-id) (number? kuukausi) (number? vuosi)]}
  (println "kommentit" tiedot)
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-valitavoitteet user urakka-id)
  (lupaukset-q/kommentit db {:lupaus-id lupaus-id
                             :urakka-id urakka-id
                             :kuukausi kuukausi
                             :vuosi vuosi}))

(defn- lisaa-kommentti
  [db user {:keys [lupaus-id urakka-id kuukausi vuosi kommentti] :as tiedot}]
  {:pre [db user tiedot (number? lupaus-id) (number? urakka-id) (number? kuukausi) (number? vuosi)
         (string? kommentti)]}
  (println "lisaa-kommentti" tiedot)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-valitavoitteet user urakka-id)
  (jdbc/with-db-transaction [db db]
                            (let [kommentti (kommentit/luo-kommentti<!
                                              db nil kommentti nil (:id user))
                                  lupaus-kommentti (lupaukset-q/lisaa-lupaus-kommentti<!
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
  (println "poista-kommentti" tiedot)
  ;; Kysely poistaa vain käyttäjän itse luomia kommentteja, joten muita tarkistuksia ei ole.
  (let [paivitetyt-rivit (lupaukset-q/poista-kayttajan-oma-kommentti!
                           db
                           {:id id
                            :kayttaja (:id user)})]
    (when-not (= paivitetyt-rivit 1)
      (throw (SecurityException. "Kommentin poistaminen epäonnistui")))
    paivitetyt-rivit))

(defrecord Lupaukset [asetukset]
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
                        (tallenna-urakan-luvatut-pisteet (:db this) user tiedot)))

    (julkaise-palvelu (:http-palvelin this)
                      :vastaa-lupaukseen
                      (fn [user tiedot]
                        (vastaa-lupaukseen (:db this) user tiedot)))

    (julkaise-palvelu (:http-palvelin this)
                      :lupauksen-vastausvaihtoehdot
                      (fn [user tiedot]
                        (lupauksen-vastausvaihtoehdot (:db this) user tiedot)))

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

    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :hae-urakan-lupaustiedot
                     :tallenna-luvatut-pisteet
                     :vastaa-lupaukseen
                     :lupauksen-vastausvaihtoehdot
                     :lupauksen-kommentit
                     :lisaa-lupauksen-kommentti
                     :poista-lupauksen-kommentti)
    this))