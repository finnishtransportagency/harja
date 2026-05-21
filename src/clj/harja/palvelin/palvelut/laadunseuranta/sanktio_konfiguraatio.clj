(ns harja.palvelin.palvelut.laadunseuranta.sanktio-konfiguraatio
  (:require [harja.domain.laadunseuranta.sanktio :as sanktio-domain]
            [harja.domain.oikeudet :as oikeudet]
            [clojure.string :as str]
            [harja.kyselyt.sanktio-konfiguraatio :as q]
            [slingshot.slingshot :refer [throw+]]))

(def ^:private urakat-laadunseuranta-sanktiot-oikeus
  (delay @(requiring-resolve 'harja.domain.oikeudet/urakat-laadunseuranta-sanktiot)))

(def ^:private sallitut-soveltuvuuskontekstit
  #{:urakka :laatupoikkeama})

(def ^:private hallinta-oikeus
  (delay @(requiring-resolve 'harja.domain.oikeudet/hallinta-laadunseuranta-profiilit)))

(def ^:private sanktio-kirjausvirhe-tyyppi :sanktio-kirjausvirhe)

(declare bonus-lajin-tehokas-nimi)

(defn- heita-sanktio-kirjausvirhe!
  [koodi viesti lisatiedot]
  (throw+ {:type sanktio-kirjausvirhe-tyyppi
           :virheet [{:koodi koodi
                      :viesti viesti}]
           :sanktio-kirjausvirhe (merge {:koodi koodi} lisatiedot)}))

(defn- laji->rivin-tyyppi
  [laji]
  (sanktio-domain/rivin-tyyppi {:laji laji
                                :bonus? (boolean (sanktio-domain/bonuslaji->teksti laji))}))

(defn- muodosta-sanktiotyyppi-dto
  [{:keys [sanktiotyyppi profiilirivi]}]
  (assoc sanktiotyyppi
    :voi-puolittaa-omailmoituksella (boolean (:voi-puolittaa-omailmoituksella profiilirivi))
    :lukitut-summat (vec (:lukitut-summat profiilirivi))))

(defn- sanktio-lajin-nayttonimi
  [rivit]
  (let [nimi (get-in (first rivit) [:laji :esitystiedot :nimi])]
    (when-not (str/blank? nimi)
      nimi)))

(defn- sanktio-lajin-tehokas-nimi
  [rivit]
  (or (sanktio-lajin-nayttonimi rivit)
    (get-in (first rivit) [:laji :nimi])))

(defn- sanktio-lajin-uudelleennimeamis-tiedot
  [rivit]
  (let [masterdatan-nimi (get-in (first rivit) [:laji :nimi])
        tehokas-nimi (sanktio-lajin-tehokas-nimi rivit)
        uudelleennimetty? (not= masterdatan-nimi tehokas-nimi)]
    {:masterdatan-nimi masterdatan-nimi
     :uudelleennimetty uudelleennimetty?
     :uudelleennimeaminen (when uudelleennimetty?
                            (str masterdatan-nimi " -> " tehokas-nimi))}))

(defn- muodosta-sanktio-laji
  [rivit]
  (let [eka-rivi (first rivit)
        laji (get-in eka-rivi [:laji :koodi])]
    {:id (get-in eka-rivi [:laji :id])
     :laji laji
     :nimi (sanktio-lajin-tehokas-nimi rivit)
     :jarjestys (get-in eka-rivi [:laji :jarjestys])
     :rivin-tyyppi (laji->rivin-tyyppi laji)
     :sanktiotyypit (mapv muodosta-sanktiotyyppi-dto rivit)}))

(defn- muodosta-sanktio-lajit
  [rivit]
  (->> rivit
    (group-by #(get-in % [:laji :koodi]))
    vals
    (map muodosta-sanktio-laji)
    (sort-by :jarjestys)
    vec))

(defn- muodosta-profiilirivit
  [rivit]
  (->> rivit
    (sort-by (juxt #(get-in % [:profiilirivi :jarjestys])
               #(get-in % [:profiilirivi :id])))
    (mapv (fn [{:keys [soveltuvuuskonteksti] :as rivi}]
            {:id (get-in rivi [:profiilirivi :id])
             :jarjestys (get-in rivi [:profiilirivi :jarjestys])
             :voi-puolittaa-omailmoituksella (boolean (get-in rivi [:profiilirivi :voi-puolittaa-omailmoituksella]))
             :lukitut-summat (vec (get-in rivi [:profiilirivi :lukitut-summat]))
             :soveltuvuuskonteksti soveltuvuuskonteksti
             :sanktiotyyppi (muodosta-sanktiotyyppi-dto rivi)}))))

(defn- muodosta-sanktio-laji-admin
  [rivit]
  (let [eka-rivi (first rivit)
        laji (get-in eka-rivi [:laji :koodi])
        {:keys [masterdatan-nimi uudelleennimetty uudelleennimeaminen]} (sanktio-lajin-uudelleennimeamis-tiedot rivit)]
    {:id (get-in eka-rivi [:laji :id])
     :laji laji
     :nimi (sanktio-lajin-tehokas-nimi rivit)
     :masterdatan-nimi masterdatan-nimi
     :uudelleennimetty uudelleennimetty
     :uudelleennimeaminen uudelleennimeaminen
     :jarjestys (get-in eka-rivi [:laji :jarjestys])
     :rivin-tyyppi (laji->rivin-tyyppi laji)
     :rivit (muodosta-profiilirivit rivit)}))

(defn- muodosta-sanktio-lajit-admin
  [rivit]
  (->> rivit
    (filter #(get-in % [:laji :id]))
    (group-by #(get-in % [:laji :koodi]))
    vals
    (map muodosta-sanktio-laji-admin)
    (sort-by :jarjestys)
    vec))

(defn- muodosta-sisalto-admin
  [rivit]
  (->> rivit
    (group-by :soveltuvuuskonteksti)
    (sort-by key)
    (mapv (fn [[soveltuvuuskonteksti kontekstin-rivit]]
            {:soveltuvuuskonteksti soveltuvuuskonteksti
             :lajit (muodosta-sanktio-lajit-admin kontekstin-rivit)}))))

(defn- muotoile-hoitovuosivali
  [{:keys [hoitovuosi-alku hoitovuosi-loppu]}]
  (str hoitovuosi-alku "-" hoitovuosi-loppu))

(defn- muotoile-paivavali
  [{:keys [alkupvm loppupvm]}]
  (str alkupvm
    (when loppupvm
      (str " - " loppupvm))))

(defn- muodosta-profiilin-yhteenveto
  [{:keys [lajimaara rivimaara soveltuvuuskontekstit] :as profiili}]
  (let [kontekstimaara (count soveltuvuuskontekstit)]
    (str/join ", "
      [(str (muotoile-hoitovuosivali profiili) " hoitovuotta")
       (str lajimaara " lajia")
       (str rivimaara " riviä")
       (str kontekstimaara " kontekstia")
       (muotoile-paivavali profiili)])))

(defn- muodosta-bonus-profiilin-yhteenveto
  [{:keys [lajimaara rivimaara] :as profiili}]
  (str/join ", "
    [(str (muotoile-hoitovuosivali profiili) " hoitovuotta")
     (str lajimaara " lajia")
     (str rivimaara " riviä")
     (muotoile-paivavali profiili)]))

(defn- taydenna-profiilin-yhteenveto
  [profiili]
  (assoc profiili :yhteenveto (muodosta-profiilin-yhteenveto profiili)))

(defn- taydenna-bonus-profiilin-yhteenveto
  [profiili]
  (assoc profiili :yhteenveto (muodosta-bonus-profiilin-yhteenveto profiili)))

(defn- hae-sanktio-profiili-admin-tiedot
  [db sanktio-profiili-id]
  (or (first (q/hae-sanktio-profiili-admin db {:sanktio_profiili_id sanktio-profiili-id}))
    (throw (IllegalArgumentException.
             (str "Sanktio-profiilia id:llä " sanktio-profiili-id " ei löytynyt.")))))

(defn- hae-bonus-profiili-admin-tiedot
  [db bonus-profiili-id]
  (or (first (q/hae-bonus-profiili-admin db {:bonus_profiili_id bonus-profiili-id}))
    (throw (IllegalArgumentException.
             (str "Bonus-profiilia id:llä " bonus-profiili-id " ei löytynyt.")))))

(defn- vaadi-kelvollinen-soveltuvuuskonteksti
  [soveltuvuuskonteksti]
  (when-not (contains? sallitut-soveltuvuuskontekstit soveltuvuuskonteksti)
    (throw (IllegalArgumentException.
             (str "Virheellinen soveltuvuuskonteksti " soveltuvuuskonteksti
               ". Sallittuja ovat :urakka ja :laatupoikkeama."))))

  soveltuvuuskonteksti)

(defn- vaadi-yksiselitteinen-sanktio-profiili
  [profiilit {:keys [urakka-id hoitovuosi soveltuvuuskonteksti]}]
  (let [osumien-maara (count profiilit)]
    (cond
      (zero? osumien-maara)
      (throw (IllegalArgumentException.
               (str "Sanktio-profiilia ei loytynyt urakalle " urakka-id
                 ", hoitovuodelle " hoitovuosi
                 " ja soveltuvuuskontekstiin " soveltuvuuskonteksti ".")))

      (= 1 osumien-maara)
      (first profiilit)

      :else
      (throw (IllegalArgumentException.
               (str "Useita aktiivisia sanktio-profiileja loytyi urakalle " urakka-id
                 ", hoitovuodelle " hoitovuosi
                 " ja soveltuvuuskontekstiin " soveltuvuuskonteksti "."))))))

(defn- vaadi-profiililla-rivit
  [profiili soveltuvuuskonteksti rivit]
  (when-not (seq rivit)
    (throw (IllegalArgumentException.
             (str "Sanktio-profiililta " (:nimi profiili)
               " puuttuu rivit soveltuvuuskontekstiin " soveltuvuuskonteksti "."))))

  rivit)

(defn- hae-sanktio-profiilin-rivit-kontekstissa
  [db {:keys [urakka-id hoitovuosi soveltuvuuskonteksti]}]
  (let [soveltuvuuskonteksti (vaadi-kelvollinen-soveltuvuuskonteksti soveltuvuuskonteksti)
        profiili (vaadi-yksiselitteinen-sanktio-profiili
                   (q/hae-urakan-sanktio-profiilit
                     db
                     {:urakka_id urakka-id
                      :hoitovuosi hoitovuosi})
                   {:urakka-id urakka-id
                    :hoitovuosi hoitovuosi
                    :soveltuvuuskonteksti soveltuvuuskonteksti})
        rivit (->> (q/hae-sanktio-profiilin-rivit
                     db
                     {:sanktio_profiili_id (:id profiili)
                      :soveltuvuuskonteksti (name soveltuvuuskonteksti)})
                (vaadi-profiililla-rivit profiili soveltuvuuskonteksti))]
    {:profiili profiili
     :soveltuvuuskonteksti soveltuvuuskonteksti
     :rivit rivit}))

(defn vaadi-sallittu-sanktiokonfiguraatiorivi
  [db {:keys [urakka-id hoitovuosi soveltuvuuskonteksti laji sanktiotyyppi-id]}]
  (let [{:keys [rivit]} (hae-sanktio-profiilin-rivit-kontekstissa
                          db
                          {:urakka-id urakka-id
                           :hoitovuosi hoitovuosi
                           :soveltuvuuskonteksti soveltuvuuskonteksti})
        lajin-rivit (filterv #(= laji (get-in % [:laji :koodi])) rivit)
        sentinel-rivi (first (filter #(= 0 (get-in % [:sanktiotyyppi :koodi])) lajin-rivit))]
    (cond
      (empty? lajin-rivit)
      (heita-sanktio-kirjausvirhe!
        :sanktiolaji-ei-sallittu
        (str "Sanktiolaji " (name laji) " ei ole sallittu urakan sanktio-konfiguraatiossa.")
        {:urakka-id urakka-id
         :hoitovuosi hoitovuosi
         :soveltuvuuskonteksti soveltuvuuskonteksti
         :laji laji
         :sanktiotyyppi-id sanktiotyyppi-id})

      (nil? sanktiotyyppi-id)
      (if (and sentinel-rivi (= 1 (count lajin-rivit)))
        sentinel-rivi
        (heita-sanktio-kirjausvirhe!
          :sanktiotyyppi-puuttuu
          (str "Sanktiotyyppi puuttuu lajille " (name laji) ".")
          {:urakka-id urakka-id
           :hoitovuosi hoitovuosi
           :soveltuvuuskonteksti soveltuvuuskonteksti
           :laji laji}))

      :else
      (or (some #(when (= sanktiotyyppi-id (get-in % [:sanktiotyyppi :id])) %) lajin-rivit)
        (heita-sanktio-kirjausvirhe!
          :sanktiotyyppi-ei-sallittu
          (str "Sanktiolaji: " (name laji)
            " ei mahdollinen sanktiotyypille id: " sanktiotyyppi-id)
          {:urakka-id urakka-id
           :hoitovuosi hoitovuosi
           :soveltuvuuskonteksti soveltuvuuskonteksti
           :laji laji
           :sanktiotyyppi-id sanktiotyyppi-id})))))

(defn hae-urakan-sanktio-konfiguraatio
  [db user {:keys [urakka-id hoitovuosi soveltuvuuskonteksti]}]
  (oikeudet/vaadi-lukuoikeus @urakat-laadunseuranta-sanktiot-oikeus user urakka-id)
  (let [{:keys [profiili rivit soveltuvuuskonteksti]} (hae-sanktio-profiilin-rivit-kontekstissa
                                                        db
                                                        {:urakka-id urakka-id
                                                         :hoitovuosi hoitovuosi
                                                         :soveltuvuuskonteksti soveltuvuuskonteksti})]
    {:profiili profiili
     :soveltuvuuskonteksti soveltuvuuskonteksti
     :sanktio-lajit (muodosta-sanktio-lajit rivit)}))

(defn hae-sanktio-profiilit-admin
  [db user]
  (oikeudet/vaadi-lukuoikeus @hallinta-oikeus user)
  (->> (q/hae-sanktio-profiilit-admin db)
    (mapv taydenna-profiilin-yhteenveto)))

(defn hae-sanktio-profiilin-detalji-admin
  [db user {:keys [sanktio-profiili-id]}]
  (oikeudet/vaadi-lukuoikeus @hallinta-oikeus user)
  (let [profiili (-> (hae-sanktio-profiili-admin-tiedot db sanktio-profiili-id)
                   taydenna-profiilin-yhteenveto)
        rivit (q/hae-sanktio-profiilin-rivit-admin db {:sanktio_profiili_id sanktio-profiili-id})]
    {:profiili profiili
     :sisalto (muodosta-sisalto-admin rivit)}))

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
        rivit (q/hae-bonus-profiilin-rivit
                db
                {:bonus_profiili_id (:id profiili)
                 :urakka_id urakka-id
                 :toimenpideinstanssi_id toimenpideinstanssi-id})]
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
