(ns harja.palvelin.palvelut.lupaus.lupaus-palvelu
  (:require [com.stuartsierra.component :as component]
            [harja.id :refer [id-olemassa?]]
            [harja.kyselyt
             [lupaus-kyselyt :as lupaus-kyselyt]
             [urakat :as urakat-q]
             [budjettisuunnittelu :as budjetti-q]
             [valikatselmus :as valikatselmus-q]
             [paatos-kyselyt :as paatos-kyselyt]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.lupaus-domain :as lupaus-domain]
            [harja.domain.roolit :as roolit]
            [harja.domain.urakka :as urakka]
            [harja.domain.kulut.valikatselmus :as valikatselmus-domain]
            [harja.domain.kulut.kustannusten-seuranta :as kustannusten-seuranta]
            [harja.palvelin.palvelut.kulut.kustannusten-seuranta :as kustannusten-seuranta-palvelu]
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

(defn maarita-urakan-tavoitehinta
  "Urakalle voidaan budjetoida tavoitehinta hoitokausittain. Päätellään siis hoitokauden järjestysnumero ja tarkistetaan urakka_tavoite taulusta,
  että mikä on kulloisenkin hoitokauden tavoitehinta."
  [db urakka-id hk-alkupvm]
  (let [;; Jotta voidaan päätellä hoitokauden numero, joudutaan hakemaan urakan tietoja
        urakan-tiedot (first (urakat-q/hae-urakka db {:id urakka-id}))
        valitun-hoitokauden-alkuvuosi (pvm/vuosi hk-alkupvm)
        kuluva-hoitokausi-nro (pvm/hoitokausivuosi->mhu-hoitovuosi-nro (:alkupvm urakan-tiedot) valitun-hoitokauden-alkuvuosi)
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

(defn hae-lupauspaatos
  "Haetaan lupaukseen liittyvä päätös hoitokaudelle"
  [db urakka-id hoitokauden-alkuvuosi]
  (let [lupauspaatos (first (paatos-kyselyt/hae-lupauspaatokset db {:urakkaid urakka-id
                                                                    :hoitokauden_alkuvuosi hoitokauden-alkuvuosi}))]
    lupauspaatos))

(defn valikatselmus-tehty-urakalle? [db urakka-id hoitokauden-alkuvuosi]
  "Onko urakalle tehty välikatselmus minä tahansa hoitokautena."
  {:pre [(number? urakka-id)]}
  (let [lupauspaatos (first (paatos-kyselyt/hae-lupauspaatokset db {:urakkaid urakka-id :hoitokauden_alkuvuosi hoitokauden-alkuvuosi}))]
    (boolean lupauspaatos)))

(defn hae-lupauksen-kustannusennusteet
  "Hakee lupauksen kaikki kustannusennusteet hoitokaudelle"
  [db lupaus-id urakka-id hoitokauden-alkuvuosi]
  (when (and lupaus-id urakka-id hoitokauden-alkuvuosi)
    ;; Haetaan kaikki kustannusennusteet kerralla
    (lupaus-kyselyt/hae-lupauksen-kaikki-kustannusennusteet 
      db {:lupaus-id lupaus-id
          :urakka-id urakka-id
          :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})))

(defn- hae-lupaus-kustannukset-jarjestettyna [db urakkaid hoitovuosi hoitokauden-alkupvm hoitokauden-loppupvm]
  (let [kustannukset (kustannusten-seuranta-palvelu/hae-urakan-kustannusten-seuranta-paaryhmittain-ilman-validointia
                       db {:urakka-id urakkaid
                           :hoitokauden-alkuvuosi hoitovuosi
                           :alkupvm hoitokauden-alkupvm
                           :loppupvm hoitokauden-loppupvm})
        kustannukset-jarjestettyna (kustannusten-seuranta/jarjesta-tehtavat kustannukset)]
    kustannukset-jarjestettyna))

(defn hae-urakan-lupaustiedot-hoitokaudelle [db {:keys [urakka-id nykyhetki
                                                        valittu-hoitokausi] :as tiedot}]
  (let [[hk-alkupvm hk-loppupvm] valittu-hoitokausi
        hoitokauden-alkuvuosi (pvm/vuosi hk-alkupvm)
        vastaus (into []
                      (lupaus-kyselyt/hae-urakan-lupaustiedot db {:urakka urakka-id
                                                                   :alkupvm hk-alkupvm
                                                                   :loppupvm hk-loppupvm}))
        ;; Selvitä hoitovuosi-nro suhteessa urakan alkuvuoteen erikoisarvoja varten
        urakan-tiedot (first (urakat-q/hae-urakka db {:id urakka-id}))
        urakan-alkuvuosi (some-> (:alkupvm urakan-tiedot) pvm/vuosi)
        hoitovuosi-nro (when (and urakan-alkuvuosi hk-alkupvm)
                         (pvm/hoitokausivuosi->mhu-hoitovuosi-nro (:alkupvm urakan-tiedot) (pvm/vuosi hk-alkupvm)))
        lupaus-idt (mapv :lupaus-id vastaus)
        erikoisarvot (if (seq lupaus-idt)
                               ;; Hae erikoisarvot yksi kerrallaan ja ryhmittele
                       (->> lupaus-idt
                         (map #(lupaus-kyselyt/hae-lupauksen-hoitovuoden-kirjauskuukaudet db {:lupaus-id %
                                                                                              :hoitovuosi-nro hoitovuosi-nro}))
                         (filter seq)
                         (map first)
                         (group-by :lupaus-id))
                       {})
        ;; Sovelletaan erikoisarvot perusarvoihin ennen domain-muunnoksia  
        vastaus (mapv (fn [r]
                        (if-let [erikoisarvo (first (get erikoisarvot (:lupaus-id r)))]
                          (-> r
                              (assoc :hoitovuosi-nro hoitovuosi-nro)
                              (assoc :hoitovuoden-erikoisarvot erikoisarvo)
                              (assoc :kirjaus-kkt (:kirjaus-kkt erikoisarvo))
                              (cond-> (not (nil? (:paatos-kk erikoisarvo))) (assoc :paatos-kk (:paatos-kk erikoisarvo)))
                              (cond-> (not (nil? (:joustovara-kkta erikoisarvo))) (assoc :joustovara-kkta (:joustovara-kkta erikoisarvo))))
                          r))
                      vastaus)
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
                     (mapv #(let [kustannusennusteet (when (= "kustannusennuste" (:lupaustyyppi %))
                                                       (hae-lupauksen-kustannusennusteet db (:lupaus-id %) urakka-id hoitokauden-alkuvuosi))]
                              (lupaus-domain/liita-lupaus-kuukaudet % nykyhetki valittu-hoitokausi hoitovuosi-nro (get erikoisarvot (:lupaus-id %)) kustannusennusteet)))
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
        ;; Hae oikaistu tavoitehinta välikatselmuksesta vertailua varten
        oikaistu-tavoitehinta-data (try
                                     (when hk-alkupvm
                                       (valikatselmus-q/hae-oikaistu-tavoitehinta 
                                         db {:urakka-id urakka-id 
                                             :hoitokauden-alkuvuosi hoitokauden-alkuvuosi}))
                                     (catch Exception e
                                       (log/warn "Oikaistun tavoitehinnan haku epäonnistui:" (.getMessage e))
                                       nil))
        kustannukset-jarjestettyna (try
                                     (when hk-alkupvm
                                       (hae-lupaus-kustannukset-jarjestettyna
                                         db urakka-id hoitokauden-alkuvuosi hk-alkupvm hk-loppupvm))
                                     (catch Exception e
                                       (log/warn "Kustannusten haku epäonnistui:" (.getMessage e))
                                       nil))
        oikaistu-tavoitehinta oikaistu-tavoitehinta-data
        oikaistu-toteutuneet-kustannukset (get-in kustannukset-jarjestettyna [:yhteensa :yht-toteutunut-summa])
        tavoitehinta-puuttuu? (not (and tavoitehinta (pos? tavoitehinta)))
        luvatut-pisteet-puuttuu? (not (:pisteet lupaus-sitoutuminen))
        tallennettu-paatos (hae-lupauspaatos db urakka-id (pvm/vuosi hk-alkupvm))
        valikatselmus-tehty? (valikatselmus-tehty-urakalle? db urakka-id hoitokauden-alkuvuosi)
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
        _ (prn "ennusteen-voi-tehda?" ennusteen-voi-tehda?)
        _ (prn "bonus-tai-sanktio" bonus-tai-sanktio tallennettu-bonus-tai-sanktio)
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
                            {:pisteet (:luvatut_pisteet tallennettu-paatos)}
                            lupaus-sitoutuminen)
     :lupausryhmat lupausryhmat
     ;; Lähtötiedot tarkistusta varten, ei välttämätöntä
     :lahtotiedot {:urakka-id urakka-id
                   :valittu-hoitokausi valittu-hoitokausi
                   :nykyhetki nykyhetki} ; Minkä hetken mukaan on laskettu
     ;; Yhteenveto
     :yhteenveto {:ennusteen-tila ennusteen-tila
                  :pisteet {:maksimi piste-maksimi
                            :ennuste piste-ennuste
                            :toteuma (or
                                       ;; Näytetään päätökseen tallennetut pisteet, jos saatavilla
                                       (:toteutuneet_pisteet tallennettu-paatos)
                                       piste-toteuma)}
                  :bonus-tai-sanktio bonus-tai-sanktio
                  :tavoitehinta (or
                                  ;; Näytetään päätökseen tallennettu tavoitehinta, jos saatavilla
                                  (:tavoitehinta tallennettu-paatos)
                                  tavoitehinta)
                  ;; Lisätään oikaistu tavoitehinta välikatselmuksesta
                  :oikaistu-tavoitehinta oikaistu-tavoitehinta
                  :oikaistu-toteutuneet-kustannukset oikaistu-toteutuneet-kustannukset
                  :odottaa-kannanottoa odottaa-kannanottoa
                  :merkitsevat-odottaa-kannanottoa merkitsevat-odottaa-kannanottoa
                  :odottaa-urakoitsijan-kannanottoa? odottaa-urakoitsijan-kannanottoa?
                  :valikatselmus-tehty-urakalle? valikatselmus-tehty?
                  :tavoitehinta-puuttuu? tavoitehinta-puuttuu?
                  :luvatut-pisteet-puuttuu? luvatut-pisteet-puuttuu?}}))

(defn- hae-urakan-lupaustiedot [db user {:keys [urakka-id valittu-hoitokausi] :as tiedot}]
  {:pre [(number? urakka-id) valittu-hoitokausi
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
  [db user {:keys [id urakka-id pisteet valittu-hoitokausi] :as tiedot}]
  (log/debug "tallenna-urakan-luvatut-pisteet tiedot " tiedot)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-valitavoitteet user urakka-id)
  (when (not (roolit/tilaajan-kayttaja? user))
    (throw (SecurityException. "Luvattujen pisteiden tallentaminen vaatii tilaajan käyttäjän.")))
  (when id
    (vaadi-lupaus-sitoutuminen-kuuluu-urakkaan db urakka-id id))
  (assert (not (valikatselmus-tehty-urakalle? db urakka-id (pvm/vuosi (first valittu-hoitokausi))))
    "Luvattuja pisteitä ei voi enää muuttaa, jos urakalle on tehty välikatselmus.")
  (jdbc/with-db-transaction [db db]
                            (let [;; lupaussitoutumisia pitää olla kannassa maksimissaan 1. Aiemmin oli vika, että frontti saattoi lähettää alkutilanteessa useita
                                  ;; rivejä ilman id:tä, ja syntyi enemmän kuin yksi aktiivinen rivi. Nyt estetään se tarkistamalla kannasta onko ko. urakalle jo tieto
                                  sitoutumistiedot-id (:id (first (lupaus-kyselyt/hae-sitoutumistiedot db {:urakka-id urakka-id})))
                                  params {:id sitoutumistiedot-id
                                          :urakka-id urakka-id
                                          :pisteet pisteet
                                          :kayttaja (:id user)}]
                              (if sitoutumistiedot-id
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


(defn- tallenna-kustannusennuste-vastaus [db user {:keys [lupaus-id urakka-id kuukausi vuosi
                                                           kustannusennuste] :as tiedot}]
  ;; EI lasketa pisteitä automaattisesti - ne lasketaan vasta välikatselmuksessa
   (let [maarapaiva (pvm/luo-pvm vuosi (dec kuukausi) 15)
         syotetty-pvm (pvm/nyt)
         hoitovuosi-alkuvuosi (pvm/hoitokauden-alkuvuosi vuosi kuukausi)

        ;; Tarkista onko kustannusennuste jo olemassa
         olemassa-oleva-id (lupaus-kyselyt/hae-kustannusennuste-id
                             db {:lupaus-id lupaus-id
                                 :urakka-id urakka-id
                                 :maarapaiva maarapaiva})]

     (if olemassa-oleva-id
      ;; Päivitä olemassa oleva
       (lupaus-kyselyt/paivita-kustannusennuste<! db
         {:id olemassa-oleva-id
          :tavoitehinta (:tavoitehinta kustannusennuste)
          :toteutuneet-kustannukset (:toteutuneet-kustannukset kustannusennuste)
          :syotetty-pvm syotetty-pvm
         ;; EI tallenneta pisteitä vielä - ne lasketaan välikatselmuksessa
          :pisteet nil
          :kayttaja (:id user)})

      ;; Luo uusi
       (lupaus-kyselyt/lisaa-kustannusennuste<! db
         {:lupaus-id lupaus-id
          :urakka-id urakka-id
          :maarapaiva maarapaiva
          :tavoitehinta (:tavoitehinta kustannusennuste)
          :toteutuneet-kustannukset (:toteutuneet-kustannukset kustannusennuste)
          :syotetty-pvm syotetty-pvm
          :hoitovuosi-alkuvuosi hoitovuosi-alkuvuosi
         ;; EI tallenneta pisteitä vielä - ne lasketaan välikatselmuksessa
          :pisteet nil
          :kayttaja (:id user)}))

    ;; Palauta kustannusennusteen tiedot
   (lupaus-kyselyt/hae-kustannusennuste db
     {:lupaus-id lupaus-id
      :urakka-id urakka-id
      :maarapaiva maarapaiva})))

(defn- tarkista-vastaus-ja-vaihtoehto
  "Tarkista vastaustyyppi lupauksen tyypin mukaan"
  [db lupaus vastaus lupaus-vaihtoehto-id kustannusennuste]
  (cond
    (= "yksittainen" (:lupaustyyppi lupaus))
    (assert (nil? lupaus-vaihtoehto-id))

    (= "kustannusennuste" (:lupaustyyppi lupaus))
    (do
      (assert (nil? vastaus))
      (assert (nil? lupaus-vaihtoehto-id))
      (assert kustannusennuste "Kustannusennuste on pakollinen")
      (assert (:tavoitehinta kustannusennuste) "Tavoitehinta on pakollinen")
      (assert (:toteutuneet-kustannukset kustannusennuste) "Toteutuneet kustannukset pakolliset"))

    (or (= "monivalinta" (:lupaustyyppi lupaus))
      (= "kysely" (:lupaustyyppi lupaus)))
    (do (assert (nil? vastaus))
      (assert (sallittu-vaihtoehto? db (:id lupaus) lupaus-vaihtoehto-id)))))

(defn- tarkista-lupaus-vastaus
  [db user {:keys [id lupaus-id urakka-id kuukausi vuosi paatos vastaus lupaus-vaihtoehto-id kustannusennuste] :as tiedot}]
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
  lupaus (first (lupaus-kyselyt/hae-lupaus db {:id lupaus-id}))
  urakan-tiedot (first (urakat-q/hae-urakka db {:id urakka-id}))
  urakan-alkupvm (:alkupvm urakan-tiedot)
  ;; Hoitovuoden järjestysnumero (1 = ensimmäinen) perustuen kuukausi/vuosi -parametreihin
  paivamaara (pvm/luo-pvm vuosi (dec kuukausi) 1)
  hoitovuosi-nro (pvm/paivamaara->mhu-hoitovuosi-nro urakan-alkupvm paivamaara)
  hoitovuoden-erikoisarvot nil #_(first (lupaus-kyselyt/hae-lupauksen-hoitovuoden-kirjauskuukaudet db {:lupaus-id lupaus-id :hoitovuosi-nro hoitovuosi-nro}))]
    (assert (false? (valikatselmus-tehty-urakalle? db urakka-id (pvm/hoitokauden-alkuvuosi vuosi kuukausi)))
            "Vastauksia ei voi enää muuttaa välikatselmuksen jälkeen")
    ;; Tarkista, että "yksittainen"-tyyppiselle lupaukselle on annettu boolean "vastaus",
    ;; ja muun tyyppiselle sallittu "lupaus-vaihtoehto-id".
    (tarkista-vastaus-ja-vaihtoehto db lupaus vastaus lupaus-vaihtoehto-id kustannusennuste)
    (when-not id
      ;; Tarkista, että kirjaus/päätös tulee sallitulle kuukaudelle.
  (assert (lupaus-domain/sallittu-kuukausi-hoitovuodelle? lupaus kuukausi paatos hoitovuosi-nro hoitovuoden-erikoisarvot)
      (str "Kuukausi " kuukausi " ei ole sallittu (paatos=" paatos ") hoitovuodelle " hoitovuosi-nro ".")))))

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
  [db user {:keys [id lupaus-id] :as tiedot}]
  {:pre [db user tiedot]}
  (log/debug "vastaa-lupaukseen " tiedot)
  (tarkista-lupaus-vastaus db user tiedot)

  (jdbc/with-db-transaction [db db]
    (if (= "kustannusennuste" (:lupaustyyppi (first (lupaus-kyselyt/hae-lupaus db {:id lupaus-id}))))
      ;; Kustannusennustelogiikka
      (tallenna-kustannusennuste-vastaus db user tiedot)
      ;; Tavallinen lupausvastaus
      (if id
        (paivita-lupaus-vastaus db (:id user) tiedot)
        (lisaa-lupaus-vastaus db (:id user) tiedot)))))

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
         sitoutumistiedot (first (lupaus-kyselyt/hae-sitoutumistiedot db {:urakka-id urakka-id}))
         ;; Kuukausittaisten pisteiden muokkaamisessa vaikuttaa tämän hoitokauden välikatselmus
         valikatselmus-tehty-hoitokaudelle? (valikatselmus-tehty-urakalle? db urakka-id (pvm/vuosi hk-alkupvm))
         ;; Koko urakkakauden sitoutumispisteisiin vaikuttaa onko urakalle tehty yhtään välitkaselmusta
         valikatselmus-tehty-urakalle? (valikatselmus-tehty-urakalle? db urakka-id vuosi)
         ;; Hae välikatselmuksen vahvistetut kustannusennusteet
         vahvistetut-kustannusennusteet (lupaus-kyselyt/hae-valikatselmuksen-vahvistetut-kustannusennusteet db {:urakka-id urakka-id
                                                                                                              :hoitokauden-alkuvuosi vuosi})
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
                   :valikatselmus-tehty-urakalle? valikatselmus-tehty-urakalle?
                   :tavoitehinta-puuttuu? tavoitehinta-puuttuu?
                   :luvatut-pisteet-puuttuu? luvatut-pisteet-puuttuu?
                   :vahvistetut-kustannusennusteet vahvistetut-kustannusennusteet}})))

(defn laske-lopullinen-kustannusennuste!
  "Laskee lopulliset pisteet kustannusennusteille kun välikatselmus on saatavilla.
   Kutsutaan välikatselmuksen päätöksestä."
  [db urakka-id hoitokauden-alkuvuosi toteutunut-tavoitehinta toteutunut-kustannus valikatselmus-pvm user-id]
  (try
    (log/info (format "Lasketaan lopulliset kustannusennuste pisteet urakalle %s hoitokaudelle %s"
                urakka-id hoitokauden-alkuvuosi))

    ;; Hae hoitovuoden alun tavoitehinta  
    (let [hk-alkupvm (pvm/hoitokauden-alkupvm hoitokauden-alkuvuosi)
          hoitovuoden-alun-tavoitehinta (maarita-urakan-tavoitehinta db urakka-id hk-alkupvm)]
      
      ;; Tallenna ensin lopputilanne
      (when (resolve 'lupaus-kyselyt/tallenna-lopputilanne!)
        (lupaus-kyselyt/tallenna-lopputilanne! db {:urakka-id urakka-id
                                                   :hoitovuosi-alkuvuosi hoitokauden-alkuvuosi
                                                   :lopullinen-tavoitehinta toteutunut-tavoitehinta
                                                   :lopulliset-kustannukset toteutunut-kustannus
                                                   :valikatselmus-pvm valikatselmus-pvm
                                                   :vahvistaja user-id}))

      ;; Hae kaikki kustannusennusteet lupausten kautta
      (let [lupaukset (lupaus-kyselyt/hae-urakan-lupaukset db {:urakka-id urakka-id})
            kustannusennuste-lupaukset (filter #(= "kustannusennuste" (:lupaustyyppi %)) lupaukset)]

        ;; Käy läpi jokainen kustannusennuste-lupaus
        (doseq [lupaus kustannusennuste-lupaukset]
          (let [lupaus-id (:id lupaus)
                kustannusennusteet (lupaus-kyselyt/hae-lupauksen-kaikki-kustannusennusteet
                                     db {:lupaus-id lupaus-id
                                         :urakka-id urakka-id
                                         :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})]

            (doseq [ke kustannusennusteet]
              (let [ennustettu-tavoitehinta (:tavoitehinta ke)
                    ennustetut-kustannukset (:toteutuneet-kustannukset ke)
                    maarapaiva (:maarapaiva ke)
                    kustannusennuste-id (:id ke)]

                ;; Laske lopulliset pisteet domain-logiikalla
                (when (and ennustettu-tavoitehinta ennustetut-kustannukset hoitovuoden-alun-tavoitehinta)
                  (let [lopulliset-pisteet (lupaus-domain/laske-kustannusennuste-tulos
                                             {:ennustettu-tavoitehinta ennustettu-tavoitehinta
                                              :ennustettu-kustannus ennustetut-kustannukset
                                              :toteutunut-tavoitehinta toteutunut-tavoitehinta
                                              :toteutunut-kustannus toteutunut-kustannus
                                              :hoitovuoden-alun-tavoitehinta hoitovuoden-alun-tavoitehinta}
                                             (pvm/kuukausi maarapaiva))]

                    ;; Päivitä lopulliset pisteet tietokantaan
                    (lupaus-kyselyt/paivita-kustannusennuste-lopulliset-pisteet!
                      db {:kustannusennuste-id kustannusennuste-id
                          :ennustettu-tavoitehinta ennustettu-tavoitehinta
                          :ennustetut-kustannukset ennustetut-kustannukset
                          :lasketut-pisteet lopulliset-pisteet
                          :muokkaaja user-id})

                    (log/info (format "Päivitettiin kustannusennuste %s lopulliset pisteet: %s"
                                kustannusennuste-id lopulliset-pisteet)))))))))

      (log/info (format "Lopulliset kustannusennuste pisteet laskettu urakalle %s hoitokaudelle %s"
                  urakka-id hoitokauden-alkuvuosi)))

    (catch Exception e
      (log/error e (format "Virhe laskettaessa lopullisia kustannusennuste pisteitä urakalle %s: %s"
                     urakka-id (.getMessage e)))
      (throw e))))

(defn laske-lopullinen-kustannusennuste
  "Laskee hoitovuoden lopulliset kustannusennusteen pisteet kustannustietojen perusteella.
   
   Tätä funktiota kutsutaan välikatselmuksessa, kun hoitokauden lopun hintapäätös tehdään.
   Funktio:
   1. Hakee toteutuneet kustannukset hoitokaudelta
   2. Kutsuu lupaus-palvelun kustannusennuste-logiikkaa 
   3. Palauttaa päivitetyn päätöksen tai nil jos laskenta epäonnistuu
   
   Parametrit:
   - db: Tietokantayhteys
   - urakka-id: Urakan tunniste
   - hoitokauden-alkuvuosi: Hoitokauden alkuvuosi (esim. 2024)
   - paatos: Välikatselmuksen päätös (map)
   - kayttaja: Käyttäjän tiedot"
  [db urakka-id hoitokauden-alkuvuosi paatos kayttaja]
  (try
    (log/info (format "Lasketaan lopulliset kustannusennuste pisteet urakalle %s hoitokaudelle %s"
                urakka-id hoitokauden-alkuvuosi))

    ;; Haetaan toteutuneet kustannukset päätöksestä
    (let [toteutunut-tavoitehinta (:tavoitehinta paatos)
          toteutuneet-kustannukset (:toteutuneet_kustannukset paatos)
          paatos-pvm (:paatosten_asettamisaika paatos)
          user-id (:id kayttaja)]

      ;; Kutsutaan aiempaa logiikkaa
      (laske-lopullinen-kustannusennuste! db urakka-id hoitokauden-alkuvuosi
        toteutunut-tavoitehinta toteutuneet-kustannukset
        paatos-pvm user-id)
      ;; Palauta päätös jossa on toteutuneet kustannukset
      (assoc paatos :toteutuneet_kustannukset toteutuneet-kustannukset))
    (catch Exception e
      (log/error e "Virhe kustannusennusteen laskennassa")
      nil)))

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


(comment
  (def j harja.palvelin.main/harja-jarjestelma)
  (hae-urakan-lupaustiedot-hoitokaudelle
    (:db j)
    {:urakka-id 36,
     :valittu-hoitokausi [#inst "2023-09-30T21:00:00.000-00:00" #inst "2024-09-30T20:59:59.000-00:00"],
     :nykyhetki #inst "2024-02-28T13:37:14.992-00:00"}))


(comment
  (def j harja.palvelin.main/harja-jarjestelma)
  (lupaus-kyselyt/hae-urakan-lupaustiedot 
    (:db j)
    {:urakka 36
     :alkupvm #inst "2023-09-30T21:00:00.000-00:00"
     :loppupvm #inst "2024-09-30T20:59:59.000-00:00"}))
