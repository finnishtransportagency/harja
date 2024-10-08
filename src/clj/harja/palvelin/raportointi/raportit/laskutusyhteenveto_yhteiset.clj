(ns harja.palvelin.raportointi.raportit.laskutusyhteenveto-yhteiset
  "Hoidon ja MHU-urakan laskutusyhteenvetojen yhteiset funktiot ja apurit"
  (:require [harja.kyselyt.laskutusyhteenveto :as laskutus-q]
            [taoensso.timbre :as log]
            [harja.kyselyt.konversio :as konv]
            [harja.pvm :as pvm]
            [clojure.string :as str]
            [harja.fmt :as fmt]
            [harja.domain.toimenpidekoodi :as toimenpidekoodit]))


(def summa-fmt fmt/euro-opt)


(defn raha-arvo-olemassa? [arvo]
  (not (or (= arvo 0.0M) (nil? arvo))))


(defn kustannuslajin-kaikki-kentat [kentan-kantanimi]
  [(keyword (str kentan-kantanimi "_laskutettu"))
   (keyword (str kentan-kantanimi "_laskutettu_ind_korotus"))
   (keyword (str kentan-kantanimi "_laskutettu_ind_korotettuna"))
   (keyword (str kentan-kantanimi "_laskutetaan"))
   (keyword (str kentan-kantanimi "_laskutetaan_ind_korotus"))
   (keyword (str kentan-kantanimi "_laskutetaan_ind_korotettuna"))])


(defn urakoiden-lahtotiedot
  [laskutusyhteenvedot]
  (into
    (sorted-map)
    (mapv (fn [urakan-laskutusyhteenveto]
            (let [talvihoidon-rivi (first (filter #(= "Talvihoito" (:nimi %)) urakan-laskutusyhteenveto))]
              {(:urakka-id talvihoidon-rivi)
               (select-keys talvihoidon-rivi
                            [:urakka-id :urakka-nimi :urakkatyyppi
                             :indeksi :perusluku
                             :suolasakko_kaytossa :lampotila_puuttuu
                             :suolasakot_laskutetaan :suolasakot_laskutettu])}))
          laskutusyhteenvedot)))


(defn aseta-sheet-nimi [[ensimmainen & muut]]
  (when ensimmainen
    (concat [(assoc-in ensimmainen [1 :sheet-nimi] "Laskutusyhteenveto")]
            muut)))


(defn tyypin-maksuerat
  [tyyppi maksuerat]
  (get maksuerat tyyppi))


(defn urakat-joissa-indeksilaskennan-perusluku-puuttuu
  [urakoiden-lahtotiedot]
  (into #{}
        (keep (fn [urakan-lahtotiedot]
                (let [tiedot (second urakan-lahtotiedot)]
                  (when (and (:indeksi tiedot)
                             (nil? (:perusluku tiedot)))
                    (:urakka-nimi (second urakan-lahtotiedot)))))
              urakoiden-lahtotiedot)))


(defn urakat-joissa-suolasakon-laskenta-epaonnistui-ja-lampotila-puuttuu
  [urakoiden-lahtotiedot]
  (into #{}
        (keep (fn [urakan-lahtotiedot]
                (let [tiedot (second urakan-lahtotiedot)]
                  (when (and (:suolasakko_kaytossa tiedot)
                             (:lampotila_puuttuu tiedot)
                             (or (nil? (:suolasakot_laskutettu tiedot))
                                 (nil? (:suolasakot_laskutetaan tiedot))))
                    (:urakka-nimi (second urakan-lahtotiedot)))))
              urakoiden-lahtotiedot)))


(defn urakoittain-kentat-joiden-laskennan-indeksipuute-sotki
  [laskutusyhteenvedot]
  (apply merge
         (mapv (fn [laskutusyhteenveto]
                 {(:urakka-nimi (first laskutusyhteenveto))
                  (into #{}
                        (apply concat
                               (keep
                                 #(keep (fn [rivin-map-entry]
                                          (when (nil? (val rivin-map-entry))
                                            (key rivin-map-entry)))
                                        (apply dissoc % (kustannuslajin-kaikki-kentat "suolasakot")))
                                 laskutusyhteenveto)))})
               laskutusyhteenvedot)))


(defn varoitus-indeksilaskennan-perusluku-puuttuu
  [urakat-joissa-indeksilaskennan-perusluku-puuttuu]
  (when-not (empty? urakat-joissa-indeksilaskennan-perusluku-puuttuu)
    (if (= 1 (count urakat-joissa-indeksilaskennan-perusluku-puuttuu))
      "Urakan indeksilaskennan perusluku puuttuu."
      (str "Seuraavissa urakoissa indeksilaskennan perusluku puuttuu: "
           (str/join ", "
                     (for [u urakat-joissa-indeksilaskennan-perusluku-puuttuu]
                       u))))))


(defn varoitus-lampotila-puuttuu
  [urakat-joissa-suolasakon-laskenta-epaonnistui-ja-lampotila-puuttuu]
  (when-not (empty? urakat-joissa-suolasakon-laskenta-epaonnistui-ja-lampotila-puuttuu)
    (str "Seuraavissa urakoissa talvisuolasakko on käytössä mutta lämpötilatieto puuttuu: "
         (str/join ", "
                   (for [u urakat-joissa-suolasakon-laskenta-epaonnistui-ja-lampotila-puuttuu]
                     u)))))


(defn urakat-joissa-suolasakon-laskenta-epaonnistui-ja-lampotila-puuttuu
  [urakoiden-lahtotiedot]
  (into #{}
        (keep (fn [urakan-lahtotiedot]
                (let [tiedot (second urakan-lahtotiedot)]
                  (when (and (:suolasakko_kaytossa tiedot)
                             (:lampotila_puuttuu tiedot)
                             (or (nil? (:suolasakot_laskutettu tiedot))
                                 (nil? (:suolasakot_laskutetaan tiedot))))
                    (:urakka-nimi (second urakan-lahtotiedot)))))
              urakoiden-lahtotiedot)))


(defn varoitus-indeksitietojen-puuttumisesta
  [varoitus-indeksilaskennan-perusluku-puuttuu
   urakat-joiden-laskennan-indeksipuute-sotki]
  (if varoitus-indeksilaskennan-perusluku-puuttuu
    varoitus-indeksilaskennan-perusluku-puuttuu
    (when-not (empty? urakat-joiden-laskennan-indeksipuute-sotki)
      (str "Seuraavissa urakoissa indeksilaskentaa ei voitu täysin suorittaa, koska tarpeellisia indeksiarvoja puuttuu: "
           (str/join ", "
                     (for [u urakat-joiden-laskennan-indeksipuute-sotki]
                       u))))))


(def varoitus-vain-jvh-voi-muokata-tietoja "Vain järjestelmän vastuuhenkilö voi syöttää indeksiarvoja ja lämpötiloja Harjaan.")


(defn tee-laskutusyhteevetohaku [kysely-fn db hk-alkupvm hk-loppupvm alkupvm haun-loppupvm urakka-id]
  (kysely-fn db
             (konv/sql-date hk-alkupvm)
             (konv/sql-date hk-loppupvm)
             (konv/sql-date alkupvm)
             (konv/sql-date haun-loppupvm)
             urakka-id))


(defn hae-alku-ja-loppupvm [alkupvm loppupvm]
  (if (or (pvm/kyseessa-kk-vali? alkupvm loppupvm)
          (pvm/kyseessa-hoitokausi-vali? alkupvm loppupvm))
    ;; jos kyseessä vapaa aikaväli, lasketaan vain yksi sarake joten
    ;; hk-pvm:illä ei ole merkitystä, kunhan eivät konfliktoi alkupvm ja loppupvm kanssa
    (pvm/paivamaaran-hoitokausi alkupvm)
    [alkupvm loppupvm]))


(defn hae-laskutusyhteenvedon-tiedot
  [db user {:keys [urakka-id alkupvm loppupvm haun-loppupvm urakkatyyppi] :as tiedot} & [koko-vuosi? vuoden-kk? valittu-aikavali?]]
  (log/debug "hae-urakan-laskutusyhteenvedon-tiedot" tiedot)

  ;; Jos valittuna tietty vuosi, vuoden kuukausi, tai oma aikaväli, käytetään annettua alku/loppupvm
  (let [[hk-alkupvm hk-loppupvm] (if (or koko-vuosi? vuoden-kk? valittu-aikavali?) 
                                   [alkupvm loppupvm] 
                                   (hae-alku-ja-loppupvm alkupvm loppupvm))
        ;; Haun-loppupvm käytetään vain, jos on koko hoitovuosi asetus valittuna. Se ei ole pakollinen parametri.
        ;; Mikäli sitä ei ole asetettu, muodostetaan se loppupvm:stä
        haun-loppupvm (if (nil? haun-loppupvm)
                        loppupvm
                        haun-loppupvm)
        kysely-fn (if (= "teiden-hoito" urakkatyyppi)
                    laskutus-q/hae-laskutusyhteenvedon-tiedot-teiden-hoito
                    laskutus-q/hae-laskutusyhteenvedon-tiedot)
        jarjesta-fn (if (= "teiden-hoito" urakkatyyppi)
                      toimenpidekoodit/tuotteen-jarjestys-mhu
                      toimenpidekoodit/tuotteen-jarjestys)
        tulos (vec
                (sort-by (juxt (comp jarjesta-fn :tuotekoodi) :nimi)
                         (into [] (tee-laskutusyhteevetohaku kysely-fn db hk-alkupvm hk-loppupvm alkupvm haun-loppupvm urakka-id))))]
    tulos))


(defn hae-tyomaa-laskutusyhteenvedon-tiedot
  [db _ {:keys [urakka-id alkupvm loppupvm haun-loppupvm]}]
  (let [[hk-alkupvm hk-loppupvm] (hae-alku-ja-loppupvm alkupvm loppupvm)

        ;; Haun-loppupvm käytetään vain, jos on koko hoitovuosi asetus valittuna. Se ei ole pakollinen parametri.
        ;; Mikäli sitä ei ole asetettu, muodostetaan se loppupvm:stä
        haun-loppupvm (if (nil? haun-loppupvm)
                        loppupvm
                        haun-loppupvm)
        kysely-fn laskutus-q/hae-tyomaakokous-laskutusyhteenveto
        tulos (vec (into [] (tee-laskutusyhteevetohaku kysely-fn db hk-alkupvm hk-loppupvm alkupvm haun-loppupvm urakka-id)))]
    tulos))
