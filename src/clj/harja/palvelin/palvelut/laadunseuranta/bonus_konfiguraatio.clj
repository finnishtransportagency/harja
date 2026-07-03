(ns harja.palvelin.palvelut.laadunseuranta.bonus-konfiguraatio
  (:require [clojure.string :as str]
            [harja.domain.laadunseuranta.sanktio :as sanktio-domain]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.bonus-konfiguraatio :as q]
            [slingshot.slingshot :refer [throw+]]))

(declare bonus-lajin-tehokas-nimi)

(def ^:private bonus-kirjausvirhe-tyyppi :bonus-kirjausvirhe)

(defn- heita-bonus-kirjausvirhe!
  [koodi viesti lisatiedot]
  (throw+ {:type bonus-kirjausvirhe-tyyppi
           :virheet [{:koodi koodi
                      :viesti viesti}]
           :bonus-kirjausvirhe (merge {:koodi koodi} lisatiedot)}))

(defn- heita-illegal-argument!
  [_koodi viesti _lisatiedot]
  (throw (IllegalArgumentException. viesti)))

(defn- laji->rivin-tyyppi
  [laji]
  (sanktio-domain/rivin-tyyppi {:laji laji
                                :bonus? (boolean (sanktio-domain/bonuslaji->teksti laji))}))

(defn- muotoile-hoitovuosivali
  [{:keys [hoitovuosi-alku hoitovuosi-loppu]}]
  (str hoitovuosi-alku "-" hoitovuosi-loppu))

(defn- muotoile-paivavali
  [{:keys [alkupvm loppupvm]}]
  (str alkupvm
    (when loppupvm
      (str " - " loppupvm))))

(defn- muodosta-bonus-profiilin-yhteenveto
  [{:keys [lajimaara rivimaara] :as profiili}]
  (str/join ", "
    [(str (muotoile-hoitovuosivali profiili) " hoitovuotta")
     (str lajimaara " lajia")
     (str rivimaara " riviä")
     (muotoile-paivavali profiili)]))

(defn- taydenna-bonus-profiilin-yhteenveto
  [profiili]
  (assoc profiili :yhteenveto (muodosta-bonus-profiilin-yhteenveto profiili)))

(defn- hae-bonus-profiili-admin-tiedot
  [db bonus-profiili-id]
  (or (first (q/hae-bonus-profiili-admin db {:bonus_profiili_id bonus-profiili-id}))
    (throw (IllegalArgumentException.
             (str "Bonus-profiilia id:llä " bonus-profiili-id " ei löytynyt.")))))

(defn- vaadi-yksiselitteinen-bonus-profiili*
  [profiilit {:keys [urakka-id hoitovuosi] :as konteksti} virhe-heittaja!]
  (let [osumien-maara (count profiilit)]
    (cond
      (zero? osumien-maara)
      (virhe-heittaja!
        :bonus-kirjausvirhe/ei-profiilia
        (str "Bonus-profiilia ei loytynyt urakalle " urakka-id
          " ja hoitovuodelle " hoitovuosi ".")
        konteksti)

      (= 1 osumien-maara)
      (first profiilit)

      :else
      (virhe-heittaja!
        :bonus-kirjausvirhe/ei-yksiselitteinen-profiili
        (str "Useita aktiivisia bonus-profiileja loytyi urakalle " urakka-id
          " ja hoitovuodelle " hoitovuosi ".")
        konteksti))))

(defn- vaadi-yksiselitteinen-bonus-profiili
  [profiilit konteksti]
  (vaadi-yksiselitteinen-bonus-profiili* profiilit konteksti heita-illegal-argument!))

(defn- vaadi-profiililla-rivit*
  [profiili toimenpideinstanssi-id rivit virhe-heittaja!]
  (when-not (seq rivit)
    (virhe-heittaja!
      :bonus-kirjausvirhe/ei-riveja
      (str "Bonus-profiililta " (:nimi profiili)
        " puuttuu rivit toimenpideinstanssiin " toimenpideinstanssi-id ".")
      {:bonusprofiili-id (:id profiili)
       :toimenpideinstanssi-id toimenpideinstanssi-id}))

  rivit)

(defn- vaadi-profiililla-rivit
  [profiili toimenpideinstanssi-id rivit]
  (vaadi-profiililla-rivit* profiili toimenpideinstanssi-id rivit heita-illegal-argument!))

(defn- bonus-lajin-nayttonimi
  [rivit]
  (let [nimi (get-in (first rivit) [:laji :esitystiedot :nimi])]
    (when-not (str/blank? nimi)
      nimi)))

(defn- bonus-lajin-tehokas-nimi
  [rivit]
  (or (bonus-lajin-nayttonimi rivit)
    (get-in (first rivit) [:laji :nimi])))

(defn- bonus-lajin-uudelleennimeamis-tiedot
  [rivit]
  (let [masterdatan-nimi (get-in (first rivit) [:laji :nimi])
        tehokas-nimi (bonus-lajin-tehokas-nimi rivit)
        uudelleennimetty? (not= masterdatan-nimi tehokas-nimi)]
    {:masterdatan-nimi masterdatan-nimi
     :uudelleennimetty uudelleennimetty?
     :uudelleennimeaminen (when uudelleennimetty?
                            (str masterdatan-nimi " -> " tehokas-nimi))}))

(defn- muodosta-bonus-laji
  [rivit]
  (let [eka-rivi (first rivit)]
    {:id (get-in eka-rivi [:laji :id])
     :laji (get-in eka-rivi [:laji :koodi])
     :nimi (bonus-lajin-tehokas-nimi rivit)
     :jarjestys (get-in eka-rivi [:laji :jarjestys])
     :rivin-tyyppi (laji->rivin-tyyppi (get-in eka-rivi [:laji :koodi]))
     :kirjaustapa (get-in eka-rivi [:laji :kirjaustapa])
     :automaattinen (boolean (get-in eka-rivi [:laji :automaattinen]))}))

(defn- muodosta-bonus-lajit
  [rivit]
  (->> rivit
    (group-by #(get-in % [:laji :koodi]))
    vals
    (map muodosta-bonus-laji)
    (sort-by :jarjestys)
    vec))

(defn- hae-bonus-profiilin-rivit-kontekstissa
  [db {:keys [urakka-id hoitovuosi toimenpideinstanssi-id]}]
  (let [profiili (vaadi-yksiselitteinen-bonus-profiili
                   (q/hae-urakan-bonus-profiilit
                     db
                     {:urakka_id urakka-id
                      :hoitovuosi hoitovuosi})
                   {:urakka-id urakka-id
                    :hoitovuosi hoitovuosi})
        rivit (->> (q/hae-bonus-profiilin-rivit
                     db
                     {:bonus_profiili_id (:id profiili)
                      :urakka_id urakka-id
                      :toimenpideinstanssi_id toimenpideinstanssi-id})
                (vaadi-profiililla-rivit profiili toimenpideinstanssi-id))]
    {:profiili profiili
     :toimenpideinstanssi-id toimenpideinstanssi-id
     :rivit rivit}))

(defn- hae-bonus-profiilin-rivit-kontekstissa-write-pathiin
  [db {:keys [urakka-id hoitovuosi toimenpideinstanssi-id]}]
  (let [profiili (vaadi-yksiselitteinen-bonus-profiili*
                   (q/hae-urakan-bonus-profiilit
                     db
                     {:urakka_id urakka-id
                      :hoitovuosi hoitovuosi})
                   {:urakka-id urakka-id
                    :hoitovuosi hoitovuosi}
                   heita-bonus-kirjausvirhe!)
        rivit (vaadi-profiililla-rivit*
                profiili
                toimenpideinstanssi-id
                (q/hae-bonus-profiilin-rivit
                  db
                  {:bonus_profiili_id (:id profiili)
                   :urakka_id urakka-id
                   :toimenpideinstanssi_id toimenpideinstanssi-id})
                heita-bonus-kirjausvirhe!)]
    {:profiili profiili
     :toimenpideinstanssi-id toimenpideinstanssi-id
     :rivit rivit}))

(defn hae-urakan-bonus-konfiguraatio
  [db user {:keys [urakka-id hoitovuosi toimenpideinstanssi-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laadunseuranta-sanktiot user urakka-id)
  (let [{:keys [profiili rivit]} (hae-bonus-profiilin-rivit-kontekstissa
                                   db
                                   {:urakka-id urakka-id
                                    :hoitovuosi hoitovuosi
                                    :toimenpideinstanssi-id toimenpideinstanssi-id})]
    {:profiili profiili
     :toimenpideinstanssi-id toimenpideinstanssi-id
     :bonus-lajit (muodosta-bonus-lajit rivit)}))

(defn vaadi-sallittu-aktiivisessa-bonus-konfiguraatiossa
  [db {:keys [urakka-id hoitovuosi toimenpideinstanssi-id bonuslaji]}]
  (let [{:keys [profiili rivit]} (hae-bonus-profiilin-rivit-kontekstissa-write-pathiin
                                   db
                                   {:urakka-id urakka-id
                                    :hoitovuosi hoitovuosi
                                    :toimenpideinstanssi-id toimenpideinstanssi-id})
        lajin-rivit (filterv #(= bonuslaji (get-in % [:laji :koodi])) rivit)]
    (when (empty? lajin-rivit)
      (heita-bonus-kirjausvirhe!
        :bonus-kirjausvirhe/laji-ei-sallittu
        (str "Bonuslaji " (name bonuslaji) " ei ole sallittu urakan bonuskonfiguraatiossa.")
        {:urakka-id urakka-id
         :hoitovuosi hoitovuosi
         :toimenpideinstanssi-id toimenpideinstanssi-id
         :bonuslaji bonuslaji
         :bonusprofiili-id (:id profiili)}))
    (first lajin-rivit)))

(defn- muodosta-bonus-profiilirivit
  [rivit]
  (->> rivit
    (sort-by (juxt #(get-in % [:profiilirivi :jarjestys])
               #(get-in % [:profiilirivi :toimenpiderajauksen-tyyppi])
               #(get-in % [:profiilirivi :toimenpide-t2-koodi])
               #(get-in % [:profiilirivi :id])))
    (mapv (fn [rivi]
            (let [rajauksen-tyyppi (get-in rivi [:profiilirivi :toimenpiderajauksen-tyyppi])
                  t2-koodi (get-in rivi [:profiilirivi :toimenpide-t2-koodi])]
              {:id (get-in rivi [:profiilirivi :id])
               :jarjestys (get-in rivi [:profiilirivi :jarjestys])
               :toimenpiderajauksen-tyyppi rajauksen-tyyppi
               :toimenpide-t2-koodi t2-koodi
               :toimenpideinstanssi-teksti (if (= :kaikki rajauksen-tyyppi) "Kaikki" t2-koodi)
               :urakkarajausten-maara (get-in rivi [:profiilirivi :urakkarajausten-maara])
               :urakat (->> (get-in rivi [:profiilirivi :urakat])
                         sort
                         vec)
               :summamaaritys (get-in rivi [:profiilirivi :summamaaritys])})))))

(defn- muodosta-bonus-laji-admin
  [rivit]
  (let [eka-rivi (first rivit)
        laji (get-in eka-rivi [:laji :koodi])
        {:keys [masterdatan-nimi uudelleennimetty uudelleennimeaminen]} (bonus-lajin-uudelleennimeamis-tiedot rivit)]
    {:id (get-in eka-rivi [:laji :id])
     :laji laji
     :nimi (bonus-lajin-tehokas-nimi rivit)
     :masterdatan-nimi masterdatan-nimi
     :uudelleennimetty uudelleennimetty
     :uudelleennimeaminen uudelleennimeaminen
     :jarjestys (get-in eka-rivi [:laji :jarjestys])
     :rivin-tyyppi (laji->rivin-tyyppi laji)
     :kirjaustapa (get-in eka-rivi [:laji :kirjaustapa])
     :automaattinen (boolean (get-in eka-rivi [:laji :automaattinen]))
     :rivit (muodosta-bonus-profiilirivit rivit)}))

(defn- muodosta-bonus-lajit-admin
  [rivit]
  (->> rivit
    (filter #(get-in % [:laji :id]))
    (group-by #(get-in % [:laji :koodi]))
    vals
    (map muodosta-bonus-laji-admin)
    (sort-by :jarjestys)
    vec))

(defn hae-bonus-profiilit-admin
  [db user]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-laadunseuranta-profiilit user)
  (->> (q/hae-bonus-profiilit-admin db)
    (mapv taydenna-bonus-profiilin-yhteenveto)))

(defn hae-bonus-profiilin-detalji-admin
  [db user {:keys [bonus-profiili-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-laadunseuranta-profiilit user)
  (let [profiili (-> (hae-bonus-profiili-admin-tiedot db bonus-profiili-id)
                   taydenna-bonus-profiilin-yhteenveto)
        rivit (q/hae-bonus-profiilin-rivit-admin db {:bonus_profiili_id bonus-profiili-id})]
    {:profiili profiili
     :lajit (muodosta-bonus-lajit-admin rivit)}))
