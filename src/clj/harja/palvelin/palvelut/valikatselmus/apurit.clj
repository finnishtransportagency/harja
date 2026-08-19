(ns harja.palvelin.palvelut.valikatselmus.apurit
  (:require [clojure.string :as str]

            [harja.pvm :as pvm]
            [harja.kokoelmat :refer [distinct-by]]
            [harja.tyokalut.yleiset :refer [round2]]
            [harja.kyselyt.urakat :as urakka-kyselyt]
            [harja.domain.lupaus-domain :as lupaus-domain]
            [harja.kyselyt.lupaus-kyselyt :as lupaus-kyselyt]
            [harja.palvelin.palvelut.valikatselmus.paatostyypit :refer [paatostyypit]]))


(defn urakan-hoitotyyppi
  "Erittäin vaativat hoitourakat merkitään päätöstauluun hoitotyyppinä MHU+"
  [erittain_vaativa_hoitourakka]
  (if erittain_vaativa_hoitourakka "MHU+" "MHU"))


(defn mahdolliset-paatokset-tyypilla [mhu-tyyppi paatokset]
  (filter #(contains? (:hoitotyyppi %) mhu-tyyppi) paatokset))


(defn mahdolliset-paatokset-urakan-alkuvuodella [urakan-alkuvuosi paatokset]
  (filter (fn [paatos]
            (<= (:urakan_alkuvuosi paatos) urakan-alkuvuosi))
    paatokset))


(defn mahdolliset-paatokset-nakyvyys-asti [urakan-alkuvuosi paatokset]
  (filter #(or (nil? (:nakyvyys_asti %))
             (and (:nakyvyys_asti %) (>= (:nakyvyys_asti %) urakan-alkuvuosi))) paatokset))


(defn mahdolliset-paatokset-nakyvyys-vuodella [kuluva-vuosi paatokset]
  (filter #(<= (:nakyvyys_alkaen %) kuluva-vuosi) paatokset))


(defn vain-yksi-paatos-per-tyyppi [paatokset]
  (let [uniikit-tyypit (map (fn [paatos]
                              (assoc paatos :uniikki-tyyppi (str (:tyyppi paatos) (:nimi paatos)))) paatokset)
        uniikit (distinct-by :uniikki-tyyppi uniikit-tyypit)
        paatokset (map (fn [paatos]
                         (dissoc paatos :uniikki-tyyppi)) uniikit)]
    paatokset))


(defn kaikki-mahdolliset-paatokset [mhu-tyyppi urakan-alkuvuosi _urakan-loppuvuosi kuluva-hoitovuosi]
  (let [mahdollset-tyypilla (mahdolliset-paatokset-tyypilla mhu-tyyppi paatostyypit)
        mahdolliset-aloitusvuodella (mahdolliset-paatokset-urakan-alkuvuodella urakan-alkuvuosi mahdollset-tyypilla)
        mahdolliset-nakyvyys-asti (mahdolliset-paatokset-nakyvyys-asti urakan-alkuvuosi mahdolliset-aloitusvuodella)
        mahdolliset-kuluvalle-vuodelle (mahdolliset-paatokset-nakyvyys-vuodella kuluva-hoitovuosi mahdolliset-nakyvyys-asti)
        paatokset (vain-yksi-paatos-per-tyyppi mahdolliset-kuluvalle-vuodelle)]
    paatokset))


(defn lisaa-paatos-virheellisena
  "Jos päätös on mukana päätöslistassa, mutta sille ei ole antaa tarkentavia tietoja, niin lisätään siihen virhe.
  Mikäli päätöstä ei löydy listasta, niin älä lisää mitään."
  [paatokset nimi virhe lisataan? jarjestys & args]
  (let [virhepaatos (merge (first args) ;; Ensimmäinen parametri on päätös
                      {:nimi nimi :virhe virhe :jarjestys jarjestys})]
    (keep identity
      (sort-by :jarjestys
        (if (some #(= (:nimi %) nimi) paatokset)
          (conj
            (filter #(not= (:nimi %) nimi) paatokset)
            ;; Jos ehdot eivät täyttyneet, niin päätöstä ei voida lisätä edes virheellisenä
            (when lisataan?
              virhepaatos))
          paatokset)))))


(defn laske-indeksikorotus-lupaukselle [db urakkaid paatos-pvm indeksi summa sanktio?]
  (let [indeksikorotus-parametrit {:pvm paatos-pvm
                                   :indeksi indeksi
                                   :maara summa
                                   :urakka-id urakkaid
                                   :sanktiolaji (if sanktio? "lupaussanktio" nil)}
        ;; Taustalla ajetaan tämmönen: SELECT korotus FROM sanktion_indeksikorotus(:pvm::DATE, :indeksi,:maara::NUMERIC, :urakka-id::INTEGER, :sanktiolaji::sanktiolaji);
        indeksikorotus (:korotus (first (lupaus-kyselyt/hae-indeksikorotus-summalle db indeksikorotus-parametrit)))]
    indeksikorotus))


(defn hoitovuosi-paattynyt?
  "Tarkista, onko saatu aika myöhemmin kuin 30.9."
  [valittu-hoitovuosi]
  (let [nyt (pvm/nyt)
        nykyvuosi (pvm/vuosi nyt)
        kuukausi (pvm/kuukausi nyt)
        ;; valittu-hoitovuosi on aina hoitokauden alkuvuosi, mutta hoitokausi päättyy vasta seuraavan vuoden syyskuussa, joten korotetaan yhdellä
        valittu-hoitovuosi (inc valittu-hoitovuosi)]
    (cond
      (> nykyvuosi valittu-hoitovuosi) true
      (and (= nykyvuosi valittu-hoitovuosi) (>= kuukausi 10)) true
      :else false)))


(defn paatos-tallennettu-tietokantaan? [tietokanta-paatokset nimi]
  (:id (first (filter #(when (= (:nimi %) nimi) %) tietokanta-paatokset))))


(defn paatos-mahdollinen? [mahdolliset-paatokset nimi]
  (boolean (seq (filter #(when (= (:nimi %) nimi) %) mahdolliset-paatokset))))


(defn hae-ketjutetusti-kumoutuvat-paatokset
  [paatokset peruttu-avain]
  (loop [kasiteltavat [peruttu-avain]
         loydetyt #{}
         tulos []]
    (if-let [avain (first kasiteltavat)]
      (let [riippuvaiset (->> paatokset
                           (filter
                             (fn [paatos]
                               (some #(= avain (:avain %))
                                 (:riippuu paatos))))
                           (remove #(contains? loydetyt (:avain %))))

            riippuvaiset-avaimet (mapv :avain riippuvaiset)]

        (recur
          (into (vec (rest kasiteltavat)) riippuvaiset-avaimet)
          (into loydetyt riippuvaiset-avaimet)
          (into tulos riippuvaiset)))

      tulos)))
