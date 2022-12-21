(ns harja.palvelin.palvelut.laadunseuranta.laadunseuranta-tulosteet
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

(defn sanktion-tai-bonuksen-perustelu [sanktio-tai-bonus]
  (let [perustelu (get-in sanktio-tai-bonus [:laatupoikkeama :paatos :perustelu])
        kuvaus (get-in sanktio-tai-bonus [:laatupoikkeama :kuvaus])]
    (if (:bonus sanktio-tai-bonus)
      (:lisatieto sanktio-tai-bonus)
      (if (:suorasanktio sanktio-tai-bonus)
        perustelu
        (str (str "Laatupoikkeaman kuvaus: " kuvaus " \n " "Päätöksen selitys: " perustelu))))))

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
    true (conj (sanktion-tai-bonuksen-perustelu r))
    true (conj (:summa r) )
    true (conj (:indeksikorjaus r))))

(defn- muodosta-otsikot [yllapitourakka?]
  (let [normaali-leveydet {:kasitelty 1.5
                           :laji 3
                           :tyyppi 2
                           :kuvaus 3.5
                           :perustelu 4.5
                           :maara 1.5
                           :indeksi 1.5}
        yllapito-leveydet {:kasitelty 1.5
                           :laji 3
                           :kohde 2
                           :kuvaus 3
                           :tyyppi 2
                           :perustelu 4.5
                           :maara 1.5
                           :indeksi 1.5}
        leveydet (if yllapitourakka?
                   yllapito-leveydet
                   normaali-leveydet)
        otsikot (cond-> []
                  true (conj {:otsikko "Käsitelty" :leveys (:kasitelty leveydet)})
                  true (conj {:otsikko "Laji" :leveys (:laji leveydet)})
                  yllapitourakka? (conj {:otsikko "Kohde" :leveys (:kohde leveydet)})
                  yllapitourakka? (conj {:otsikko "Kuvaus" :leveys (:kuvaus leveydet)})
                  (not yllapitourakka?) (conj {:otsikko "Tyyppi" :leveys (:tyyppi leveydet)})
                  (not yllapitourakka?) (conj {:otsikko "Tapahtumapaikka/kuvaus" :leveys (:kuvaus leveydet)})
                  true (conj {:otsikko "Perustelu" :leveys (:perustelu leveydet)})
                  true (conj {:otsikko "Määrä (€)" :leveys (:maara leveydet) :tasaa :oikea :fmt :raha})
                  true (conj {:otsikko "Indeksi (€)" :leveys (:indeksi leveydet) :tasaa :oikea :fmt :raha}))]
    otsikot))
(defn sanktiot-ja-bonukset-raportti
  [alkupvm loppupvm urakan-nimi yllapitourakka? valitut-lajit kaikki-lajit rivit]
  (let [raportin-nimi "Sanktiot, bonukset ja arvonvähennykset"
        otsikot (muodosta-otsikot yllapitourakka?)
        taulukko-rivit (into []
                         (concat
                           (for [r rivit]
                             (muodosta-taulukon-rivi r yllapitourakka?))))
        yht-kpl (count rivit)
        yht-summa (apply + (map #(if (:summa %) (:summa %) 0) rivit))
        yht-indeksit (apply + (map #(if (:indeksikorjaus %) (:indeksikorjaus %) 0) rivit))
        yht-rivi (cond-> []
                   true (conj "Yht.")
                   true (conj (str yht-kpl " kpl"))
                   yllapitourakka? (conj nil)
                   yllapitourakka? (conj nil)
                   (not yllapitourakka?) (conj nil)
                   (not yllapitourakka?) (conj nil)
                   true (conj nil)
                   true (conj yht-summa)
                   true (conj yht-indeksit))
        taulukko-rivit (conj taulukko-rivit yht-rivi)
        taulukko [:taulukko {:viimeinen-rivi-yhteenveto? true
                             :sheet-nimi raportin-nimi}
                  otsikot
                  taulukko-rivit]
        checkboxit (cond-> []
                     (contains? kaikki-lajit :bonukset) (conj ["Bonukset" (contains? valitut-lajit :bonukset) 10])
                     (contains? kaikki-lajit :muistutukset) (conj ["Muistutukset" (contains? valitut-lajit :muistutukset) 10])
                     (contains? kaikki-lajit :sanktiot) (conj ["Sanktiot" (contains? valitut-lajit :sanktiot) 10])
                     (contains? kaikki-lajit :arvonvahennykset) (conj ["Arvonvähennykset" (contains? valitut-lajit :arvonvahennykset) 10]))]
    [:raportti {:nimi raportin-nimi
                :raportin-yleiset-tiedot {:raportin-nimi raportin-nimi
                                          :urakka urakan-nimi
                                          :alkupvm (pvm/pvm-opt alkupvm)
                                          :loppupvm (pvm/pvm-opt loppupvm)
                                          :viimeinen-rivi-yhteenveto? true}
                :tietoja [["Urakka" urakan-nimi]
                          ["Aika" (str (pvm/pvm-opt alkupvm) "-" (pvm/pvm-opt loppupvm))]]}
     [:teksti-paksu "Näytettävät lajit:" ]
     [:checkbox-lista checkboxit]
     taulukko]))
