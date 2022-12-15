(ns harja.palvelin.palvelut.laadunseuranta.laadunseuranta-pdf
  (:require [harja.tyokalut.xsl-fo :as xsl-fo]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.domain.laadunseuranta.sanktio :as domain-sanktio]
            [harja.domain.yllapitokohde :as yllapitokohde-domain]
            [harja.domain.tierekisteri :as tierekisteri]))

(def ^:private border "solid 0.1mm black")

(def ^:private borders {:border-bottom border
                        :border-top border
                        :border-left border
                        :border-right border})

(defn- valiotsikko-rivi [otsikko]
  [:fo:block {:margin-top "4mm"
              :margin-bottom "2mm"}
   [:fo:block {:font-weight "bold"
               :font-size 12}
    (str otsikko)]])

(defn- rivi [otsikko & sisaltosolut]
  [:fo:table-row
   [:fo:table-cell
    [:fo:block {:margin-bottom "2mm"}
     [:fo:block {:font-size 8} otsikko]]]
   (for [sisalto sisaltosolut]
     [:fo:table-cell
      [:fo:block sisalto]])])

(defn- taulukko [rivit]
  [:fo:table (merge borders {:table-layout "fixed"})
   [:fo:table-column {:column-width "10%"}]
   [:fo:table-column {:column-width "10%"}]
   [:fo:table-column {:column-width "10%"}]
   [:fo:table-column {:column-width "10%"}]
   [:fo:table-column {:column-width "10%"}]
   [:fo:table-column {:column-width "10%"}]
   [:fo:table-column {:column-width "10%"}]
   [:fo:table-body
    (for [r rivit]
      [:fo:table-row
       [:fo:table-cell (:kasittelyaika r)]
       [:fo:table-cell (:laji r)]
       [:fo:table-cell (:tyyppi r)]
       [:fo:table-cell (:kuvaus r)]
       [:fo:table-cell (:perustelu r)]
       [:fo:table-cell (:maara r)]
       [:fo:table-cell (:indeksi r)]])]])

(defn- sanktion-tai-bonuksen-kuvaus [{:keys [suorasanktio laatupoikkeama] :as sanktio-tai-bonus}]
  ;; Bonuksilla ei tällä hetkellä ole kuvausta.
  ;; Näytetään sanktion kohde, mikäli kyseessä on suorasanktio, eli sanktio on tehty sanktiolomakkeella.
  ;; Jos kyse on laatupoikkeaman kautta tehdystä sanktiosta, näytetään kohteen kuvaus ja mahdollinen TR-osoite.
  (let [kohde (:kohde laatupoikkeama)]
    (if suorasanktio
      (or kohde "–")
      (str "Laatupoikkeama: " kohde " \n "
        (when (get-in laatupoikkeama [:tr :numero])
          (str " (" (tierekisteri/tierekisteriosoite-tekstina (:tr laatupoikkeama) {:teksti-tie? true}) ")"))))))

(defn- muodosta-taulukon-rivi [r yllapitourakka?]
  (cond-> []
    true (conj (pvm/pvm-opt (:kasittelyaika r)))
    true (conj (domain-sanktio/sanktiolaji->teksti (:laji r)))
    yllapitourakka? (conj (if (get-in r [:yllapitokohde :id])
                            (yllapitokohde-domain/yllapitokohde-tekstina {:kohdenumero (get-in r [:yllapitokohde :numero])
                                                                          :nimi (get-in r [:yllapitokohde :nimi])})
                            "Ei liity kohteeseen"))
    yllapitourakka? (conj (domain-sanktio/yllapidon-sanktiofraasin-nimi (:vakiofraasi r)))
    (not yllapitourakka?) (conj (cond
                                  (and (:tyyppi r) (= "Ei tarvita sanktiotyyppiä" (get-in r [:tyyppi :nimi]))) "–"
                                  (and (:tyyppi r) (not= "Ei tarvita sanktiotyyppiä" (get-in r [:tyyppi :nimi]))) (get-in r [:tyyppi :nimi])
                                  :else "–"))
    (not yllapitourakka?) (conj (sanktion-tai-bonuksen-kuvaus r))
    true (conj (get-in r [:laatupoikkeama :paatos :perustelu]))
    true (conj (fmt/euro-opt false (:summa r)))
    true (conj (fmt/euro-opt false (:indeksikorjaus r)))))

(defn sanktiot-ja-bonukset-pdf
  [alkupvm loppupvm urakan-nimi yllapitourakka? valitut-lajit rivit]
  (let [raportin-nimi "Sanktiot, bonukset ja arvonvähennykset"
        normaali-leveydet {:kasitelty 1.5
                           :laji 3
                           :tyyppi 2
                           :kuvaus 3.5
                           :perustelu 4.5
                           :maara 1.5
                           :indeksi 1.5}
        normaali-kokonais-leveys (apply + (vals normaali-leveydet))
        yllapito-leveydet {:kasitelty 1.5
                           :laji 3
                           :kohde 2
                           :kuvaus 3
                           :tyyppi 2
                           :perustelu 4.5
                           :maara 1.5
                           :indeksi 1.5}
        yllapito-kokonais-leveys (apply + (vals normaali-leveydet))
        leveydet (if yllapitourakka?
                   yllapito-leveydet
                   normaali-leveydet)
        kokonaisleveys (if yllapitourakka?
                         yllapito-kokonais-leveys
                         normaali-kokonais-leveys)
        otsikot [{:otsikko "Käsitelty" :leveys (str (* 100 (/ (:kasitelty leveydet) kokonaisleveys)) "%")}
                 {:otsikko "Laji" :leveys (str (* 100 (/ (:laji leveydet) kokonaisleveys)) "%")}
                 (when yllapitourakka?
                   {:otsikko "Kohde" :leveys (str (* 100 (/ (:kohde leveydet) kokonaisleveys)) "%")})
                 (if yllapitourakka?
                   {:otsikko "Kuvaus" :leveys (str (* 100 (/ (:kuvaus leveydet) kokonaisleveys)) "%")}
                   {:otsikko "Tyyppi" :leveys (str (* 100 (/ (:tyyppi leveydet) kokonaisleveys)) "%")})
                 (when (not yllapitourakka?)
                   {:otsikko "Tapahtumapaikka/kuvaus" :leveys (str (* 100 (/ (:kuvaus leveydet) kokonaisleveys)) "%")})
                 {:otsikko "Perustelu" :leveys (str (* 100 (/ (:perustelu leveydet) kokonaisleveys)) "%")}
                 {:otsikko "Määrä (€)" :leveys (str (* 100 (/ (:maara leveydet) kokonaisleveys)) "%") :tasaa :oikea}
                 {:otsikko "Indeksi (€)" :leveys (str (* 100 (/ (:indeksi leveydet) kokonaisleveys)) "%") :tasaa :oikea}]
        taulukko-rivit (into []
                         (concat
                           (for [r rivit]
                             (muodosta-taulukon-rivi r yllapitourakka?))))
        yht-kpl (count rivit)
        yht-summa (apply + (map #(if (:summa %) (:summa %) 0) rivit))
        yht-indeksit (apply + (map :indeksikorjaus rivit))
        yht-rivi (cond-> []
                   true (conj "Yht.")
                   true (conj (str yht-kpl " kpl"))
                   yllapitourakka? (conj nil)
                   yllapitourakka? (conj nil)
                   (not yllapitourakka?) (conj nil)
                   (not yllapitourakka?) (conj nil)
                   true (conj nil)
                   true (conj (fmt/euro-opt false yht-summa))
                   true (conj (fmt/euro-opt false yht-indeksit)))
        taulukko-rivit (conj taulukko-rivit yht-rivi)
        taulukko [:taulukko {:viimeinen-rivi-yhteenveto? true}
                  otsikot
                  taulukko-rivit]]
    [:raportti {:nimi raportin-nimi
                :raportin-yleiset-tiedot {:raportin-nimi raportin-nimi
                                          :urakka urakan-nimi
                                          :alkupvm (pvm/pvm-opt alkupvm)
                                          :loppupvm (pvm/pvm-opt loppupvm)
                                          :viimeinen-rivi-yhteenveto? true}
                :tietoja [["Urakka" urakan-nimi]
                          ["Aika" (str (pvm/pvm-opt alkupvm) "-" (pvm/pvm-opt loppupvm))]]}
     [:teksti-paksu "Näytettävät lajit:" ]
     [:checkbox-lista [["Bonukset" (contains? valitut-lajit :bonukset) 10]
                       ["Muistutukset" (contains? valitut-lajit :muistutukset) 10]
                       ["Sanktiot" (contains? valitut-lajit :sanktiot) 10]
                       ["Arvonvähennykset" (contains? valitut-lajit :arvonvahennykset) 10]]]
     taulukko]))
