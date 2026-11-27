(ns harja.palvelin.palvelut.yllapitokohteet.tiemerkinta-kustannukset-excel
  "Vie tiemerkintäurakan päällystyskohteiden tiemerkintäkustannukset Exceliin ja tuo samat tiedot Excelistä sisään."
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [dk.ative.docjure.spreadsheet :as xls]
            [harja.domain.oikeudet :as oikeudet]
            [harja.fmt :as fmt]
            [harja.kyselyt.kustannusten-kirjaus :as q]
            [harja.kyselyt.urakat :as q-urakat]
            [harja.palvelin.raportointi.excel :as excel]
            [harja.pvm :as pvm]))

(def otsikot ["HARJA-ID" "YHA-ID" "Kohdenro" "Tieosoite" "Nimi" "PK-lk"
              "Linjamerkinnät (€)" "Pienmerkinnät (€)" "Jyrsinnät (€)" "Muut kustannukset (€)"
              "Kokonaishinta"])

(def kustannussarakkeet
  "Muokattavat kustannussarakkeet järjestyksessä. Sarakeindeksi on Excelin 0-pohjainen sarakeindeksi."
  [{:avain :linjamerkinnat :sarake 6}
   {:avain :pienmerkinnat :sarake 7}
   {:avain :jyrsinnat :sarake 8}
   {:avain :muut-kustannukset :sarake 9}])

(def ^:private eka-kustannussarake "G")
(def ^:private viim-kustannussarake "J")
;; Excel-raporttikehys sijoittaa raportin tiedot riville 1, otsikot riville 3 ja datan riviltä 4 alkaen.
(def ^:private eka-datarivi 4)
;; NUMERIC(10, 2)
(def ^:private suurin-sallittu-summa 100000000M)
;; Data päättyy yhteenvetoriviin. Sen jälkeen raporttikehys lisää mm. "Ajettu"-rivin, jota ei lueta.
(def ^:private yhteenvetoteksti "Yhteensä:")
(def ^:private yhteenvetosarake 4)

(defn- haettu-kohdenumero
  "Kysely palauttaa puuttuvan kohdenumeron merkkinä '-'. Excelissä se esitetään tyhjänä."
  [kohdenumero]
  (when-not (or (str/blank? kohdenumero) (= "-" kohdenumero))
    kohdenumero))

(defn- excelin-rivi [{:keys [id yha-id kohdenumero nimi pk-luokka] :as kohde}]
  (into [id
         yha-id
         (haettu-kohdenumero kohdenumero)
         (fmt/tieosoite-lyhyt-muoto kohde)
         nimi
         pk-luokka]
    (concat
      (map #(get kohde (:avain %)) kustannussarakkeet)
      [[:kaava {:kaava :summaa-vieressaolevat
                :alkusarake eka-kustannussarake
                :loppusarake viim-kustannussarake}]])))

(defn muodosta-excelrivit [kohteet]
  (let [kohderivit (mapv excelin-rivi kohteet)
        tyhja-rivi (vec (repeat (count otsikot) nil))
        loppurivi (+ eka-datarivi (count kohderivit) -1)
        yhteenvetorivi (into [nil nil nil nil yhteenvetoteksti nil]
                         (repeat (inc (count kustannussarakkeet))
                           [:kaava {:kaava :summaa-yllaolevat
                                    :alkurivi eka-datarivi
                                    :loppurivi loppurivi}]))]
    (concat kohderivit
      [tyhja-rivi tyhja-rivi]
      [yhteenvetorivi])))

(defn- excelin-sarakkeet []
  (let [[harja-id yha-id kohdenro tieosoite nimi pk-lk & loput] otsikot
        kokonaishinta (last loput)
        kustannusotsikot (butlast loput)]
    (into [{:otsikko harja-id :tasaa :oikea :fmt :kokonaisluku}
           {:otsikko yha-id :tasaa :oikea :fmt :kokonaisluku}
           {:otsikko kohdenro :tasaa :oikea}
           {:otsikko tieosoite}
           {:otsikko nimi}
           {:otsikko pk-lk}]
      (concat
        (map (fn [otsikko] {:otsikko otsikko :fmt :raha :voi-muokata? true}) kustannusotsikot)
        [{:otsikko kokonaishinta :fmt :raha}]))))

(defn hae-kohteet
  "Hakee urakan päällystyskohteiden tiemerkintäkustannukset valitulle vuodelle."
  [db urakka-id vuosi]
  (q/hae-urakan-yllapitokohteiden-kustannukset db {:urakka urakka-id
                                                   :yllapitokohdetyotyyppi "paallystys"
                                                   :vuosi vuosi}))

(defn vie-tiemerkintakustannukset-exceliin
  [db workbook user {:keys [urakka-id vuosi]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-tiemerkinta-kustannukset user urakka-id)
  (let [urakka (first (q-urakat/hae-urakka db urakka-id))
        kohteet (hae-kohteet db urakka-id vuosi)
        optiot {:nimi "Päällystyskohteiden tiemerkintäkustannukset"
                :sheet-nimi "HARJAAN"
                :varjele-sheet-muokkauksilta? true
                :tyhja (when (empty? kohteet) "Ei päällystyskohteita.")}
        tiedostonimi (str (:nimi urakka) "-Tiemerkintäkustannukset-" vuosi)
        raportti [:raportti {:nimi tiedostonimi
                             :raportin-yleiset-tiedot {:custom-ylin-rivi "TIEDONSIIRTOLOMAKE HARJAAN"
                                                       :raportin-nimi "Päällystyskohteiden tiemerkintäkustannukset"
                                                       :urakka (:nimi urakka)
                                                       :alkupvm (pvm/kokovuosi-ja-kuukausi (pvm/vuoden-eka-pvm vuosi))
                                                       :loppupvm (pvm/kokovuosi-ja-kuukausi (pvm/vuoden-viim-pvm vuosi))}
                             :orientaatio :landscape
                             :viimeinen-rivi-yhteenveto? true}
                  [:taulukko optiot (excelin-sarakkeet) (muodosta-excelrivit kohteet)]]]
    (excel/muodosta-excel raportti workbook)))

(defn- lue-solu [rivi sarake]
  (when-let [solu (.getCell rivi (int sarake))]
    (let [arvo (xls/read-cell solu)]
      (if (string? arvo)
        (let [arvo (str/trim arvo)]
          (when-not (str/blank? arvo) arvo))
        arvo))))

(defn- lue-kohde-id [arvo]
  (cond
    (and (number? arvo) (== arvo (Math/floor (double arvo)))) (long arvo)
    (and (string? arvo) (re-matches #"\d+" arvo)) (Long/parseLong arvo)
    :else nil))

(defn- lue-summa
  "Palauttaa [summa virhe]. Tyhjä solu tulkitaan nollaksi."
  [arvo]
  (let [summa (cond
                (nil? arvo) 0M
                (number? arvo) (bigdec arvo)
                (string? arvo) (try
                                 (bigdec (-> arvo
                                           (str/replace #"[\s\u00A0€]" "")
                                           (str/replace "," ".")))
                                 (catch NumberFormatException _ nil)))
        ;; Pyöristetään ennen rajatarkistusta, jotta pyöristys ei voi ylittää sarakkeen kokoa.
        summa (some-> ^BigDecimal summa (.setScale 2 java.math.RoundingMode/HALF_UP))]
    (cond
      (nil? summa) [nil "ei ole numero"]
      (neg? summa) [nil "ei saa olla negatiivinen"]
      (>= summa suurin-sallittu-summa) [nil "on liian suuri"]
      :else [summa nil])))

(defn- parsi-rivi
  "Palauttaa {:rivi n :id id :kustannukset {...} :virheet [...]} tai nil, jos rivillä ei ole HARJA-ID:tä."
  [rivi]
  (let [rivinumero (inc (.getRowNum rivi))
        id-arvo (lue-solu rivi 0)]
    (when (some? id-arvo)
      (if-let [id (lue-kohde-id id-arvo)]
        (let [luetut (map (fn [{:keys [avain sarake]}]
                            (let [[summa virhe] (lue-summa (lue-solu rivi sarake))]
                              {:avain avain :summa summa
                               :virhe (when virhe (str (nth otsikot sarake) " " virhe))}))
                       kustannussarakkeet)]
          {:rivi rivinumero
           :id id
           :kustannukset (into {} (map (juxt :avain :summa)) luetut)
           :virheet (into [] (keep :virhe) luetut)})
        {:rivi rivinumero
         :virheet ["HARJA-ID ei ole kelvollinen"]}))))

(defn parsi-tiemerkintakustannukset
  "Lukee Excelin ensimmäiseltä sheetiltä kohteiden kustannukset.
  Palauttaa {:rivit [...]} tai {:virheet [...]}, jos tiedoston rakenne on väärä."
  [workbook]
  (let [rivit (->> (xls/sheet-seq workbook)
                first
                xls/row-seq
                (remove nil?))
        otsikkorivi (first (filter #(= (first otsikot) (lue-solu % 0)) rivit))
        luetut-otsikot (when otsikkorivi
                         (mapv #(lue-solu otsikkorivi %) (range (count otsikot))))]
    (if (not= otsikot luetut-otsikot)
      {:virheet ["Tiedoston rakenne ei vastaa mallipohjaa. Lataa pohja Lataa Excel -napista."]}
      {:rivit (into []
                (comp (take-while #(not= yhteenvetoteksti (lue-solu % yhteenvetosarake)))
                      (keep parsi-rivi))
                (rest (drop-while #(not= otsikkorivi %) rivit)))})))

(defn validoi-rivit
  "Palauttaa listan virheviestejä. Tyhjä lista tarkoittaa, että rivit ovat kunnossa."
  [rivit sallitut-idt]
  (let [id-maarat (frequencies (keep :id rivit))]
    (into []
      (mapcat (fn [{:keys [rivi id virheet]}]
                (map #(str "Rivi " rivi ": " %)
                  (cond-> virheet
                    (and id (not (contains? sallitut-idt id)))
                    (conj (str "Kohdetta HARJA-ID:llä " id " ei löydy urakan valitun vuoden päällystyskohteista"))

                    (and id (< 1 (get id-maarat id 0)))
                    (conj (str "HARJA-ID " id " esiintyy tiedostossa useammin kuin kerran"))))))
      rivit)))

(defn- tallenna-kohteen-kustannukset! [db user {:keys [id kustannukset]}]
  (let [tiedot (assoc kustannukset
                 :id id
                 :muokkaaja (:id user)
                 :muokattu (pvm/nyt))]
    (if (empty? (q/hae-yllapitokustannus db {:yllapitokohde id}))
      (q/lisaa-tiemerkinta-yllapitokohde-kustannuskirjaus! db (assoc tiedot :luoja (:id user)))
      (q/paivita-tiemerkinta-yllapitokohde-kustannuskirjaus! db tiedot))))

(defn tuo-tiemerkintakustannukset-excelista
  "Validoi ja tallentaa Excelin kustannukset. Jos yksikin rivi on virheellinen, mitään ei tallenneta.
  Palauttaa {:tallennettu n} tai {:virheet [...]}."
  [db user workbook urakka-id vuosi]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-tiemerkinta-kustannukset user urakka-id)
  (let [{:keys [rivit virheet]} (parsi-tiemerkintakustannukset workbook)]
    (cond
      (seq virheet)
      {:virheet virheet}

      (empty? rivit)
      {:virheet ["Tiedostosta ei löytynyt yhtään kohdetta."]}

      :else
      (let [sallitut-idt (into #{} (map :id) (hae-kohteet db urakka-id vuosi))
            virheet (validoi-rivit rivit sallitut-idt)]
        (if (seq virheet)
          {:virheet virheet}
          (jdbc/with-db-transaction [db db]
            (doseq [rivi rivit]
              (tallenna-kohteen-kustannukset! db user rivi))
            {:tallennettu (count rivit)}))))))
