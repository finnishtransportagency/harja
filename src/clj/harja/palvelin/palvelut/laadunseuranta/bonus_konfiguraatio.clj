(ns harja.palvelin.palvelut.laadunseuranta.bonus-konfiguraatio
  (:require [clojure.string :as str]
            [harja.domain.laadunseuranta.sanktio :as sanktio-domain]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.bonus-konfiguraatio :as q]))

(def ^:private urakat-laadunseuranta-sanktiot-oikeus
  (delay @(requiring-resolve 'harja.domain.oikeudet/urakat-laadunseuranta-sanktiot)))

(def ^:private hallinta-oikeus
  (delay @(requiring-resolve 'harja.domain.oikeudet/hallinta-laadunseuranta-profiilit)))

(declare bonus-lajin-tehokas-nimi)

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

(defn- vaadi-yksiselitteinen-bonus-profiili
  [profiilit {:keys [urakka-id hoitovuosi]}]
  (let [osumien-maara (count profiilit)]
    (cond
      (zero? osumien-maara)
      (throw (IllegalArgumentException.
               (str "Bonus-profiilia ei loytynyt urakalle " urakka-id
                 " ja hoitovuodelle " hoitovuosi ".")))

      (= 1 osumien-maara)
      (first profiilit)

      :else
      (throw (IllegalArgumentException.
               (str "Useita aktiivisia bonus-profiileja loytyi urakalle " urakka-id
                 " ja hoitovuodelle " hoitovuosi "."))))))

(defn- vaadi-profiililla-rivit
  [profiili toimenpideinstanssi-id rivit]
  (when-not (seq rivit)
    (throw (IllegalArgumentException.
             (str "Bonus-profiililta " (:nimi profiili)
               " puuttuu rivit toimenpideinstanssiin " toimenpideinstanssi-id "."))))

  rivit)

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

(defn hae-urakan-bonus-konfiguraatio
  [db user {:keys [urakka-id hoitovuosi toimenpideinstanssi-id]}]
  (oikeudet/vaadi-lukuoikeus @urakat-laadunseuranta-sanktiot-oikeus user urakka-id)
  (let [{:keys [profiili rivit]} (hae-bonus-profiilin-rivit-kontekstissa
                                   db
                                   {:urakka-id urakka-id
                                    :hoitovuosi hoitovuosi
                                    :toimenpideinstanssi-id toimenpideinstanssi-id})]
    {:profiili profiili
     :toimenpideinstanssi-id toimenpideinstanssi-id
     :bonus-lajit (muodosta-bonus-lajit rivit)}))

(defn- muodosta-bonus-profiilirivit
  [rivit]
  (->> rivit
    (sort-by (juxt #(get-in % [:profiilirivi :jarjestys])
               #(get-in % [:profiilirivi :toimenpideinstanssi-rajauksen-tyyppi])
               #(get-in % [:profiilirivi :toimenpideinstanssi-t2-koodi])
               #(get-in % [:profiilirivi :id])))
    (mapv (fn [rivi]
            (let [rajauksen-tyyppi (get-in rivi [:profiilirivi :toimenpideinstanssi-rajauksen-tyyppi])
                  t2-koodi (get-in rivi [:profiilirivi :toimenpideinstanssi-t2-koodi])]
              {:id (get-in rivi [:profiilirivi :id])
               :jarjestys (get-in rivi [:profiilirivi :jarjestys])
               :toimenpideinstanssi-rajauksen-tyyppi rajauksen-tyyppi
               :toimenpideinstanssi-t2-koodi t2-koodi
               :toimenpideinstanssi-teksti (if (= :kaikki rajauksen-tyyppi) "Kaikki" t2-koodi)
               :urakkarajausten-maara (get-in rivi [:profiilirivi :urakkarajausten-maara])
               :urakat (->> (get-in rivi [:profiilirivi :urakat])
                         sort
                         vec)})))))

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
  (oikeudet/vaadi-lukuoikeus @hallinta-oikeus user)
  (->> (q/hae-bonus-profiilit-admin db)
    (mapv taydenna-bonus-profiilin-yhteenveto)))

(defn hae-bonus-profiilin-detalji-admin
  [db user {:keys [bonus-profiili-id]}]
  (oikeudet/vaadi-lukuoikeus @hallinta-oikeus user)
  (let [profiili (-> (hae-bonus-profiili-admin-tiedot db bonus-profiili-id)
                   taydenna-bonus-profiilin-yhteenveto)
        rivit (q/hae-bonus-profiilin-rivit-admin db {:bonus_profiili_id bonus-profiili-id})]
    {:profiili profiili
     :lajit (muodosta-bonus-lajit-admin rivit)}))
