(ns harja.domain.raportointi
  (:require [harja.domain.roolit :as roolit]
            #?(:clj
               [dk.ative.docjure.spreadsheet :as excel])))

;; API to colors that can be used in Excel:
;; https://poi.apache.org/apidocs/4.0/org/apache/poi/ss/usermodel/IndexedColors.html

(def virhetyylit
  {:virhe    "rgb(221,0,0)"
   :varoitus "rgb(255,153,0)"
   :info     "rgb(0,136,204)"})


;; rajat-excel, virhetyylit-excel, ja solun-oletustyyli-excel ovat exceliin sidottuja,
;; ja täten niiden ei välttämättä tarvitsisi olla domain-namespacessa. On kuitenkin
;; perusteltua pitää ne täällä sen takia, että se helpottaa virhetyylit ja virhetyylit-
;; excel pitämistä synkassa. Haluamme tietenkin, että huomiovärit ovat kaikissa kolmessa
;; formaatissa edes melkein samat.

(def rajat-excel {:border-left   :thin
                  :border-right  :thin
                  :border-bottom :thin
                  :border-top    :thin})

;; https://poi.apache.org/apidocs/org/apache/poi/ss/usermodel/IndexedColors.html
(def virhetyylit-excel
  {:virhe (merge rajat-excel
                 {:background :dark_red
                  :font {:color :white}})
   :varoitus (merge rajat-excel
                    {:background :orange
                     :font {:color :black}})
   :info (merge rajat-excel
                {:background :light_turquoise
                 :font {:color :black}})
   :disabled {:font {:color :grey_80_percent}}})

(defn solun-oletustyyli-excel [lihavoi? korosta? korosta-hennosti? korosta-harmaa?]
  (let [deep-merge (partial merge-with merge)]
    (cond-> {}
            lihavoi?
            (merge {:font {:bold true}})

            korosta?
            (deep-merge (merge rajat-excel {:background :dark_blue
                                            :font       {:color :white}}))

            korosta-hennosti?
            (deep-merge {:background :pale_blue
                         :font       {:color :black}})

            korosta-harmaa?
            (deep-merge (merge rajat-excel {:background :grey_25_percent
                                            :font {:color :black}})))))

(defn varillinen-teksti [tyyli teksti]
  [:varillinen-teksti {:arvo teksti :tyyli tyyli}])

(def info-solu (partial varillinen-teksti :info))
(def varoitus-solu (partial varillinen-teksti :varoitus))
(def virhe-solu (partial varillinen-teksti :virhe))

(defn raporttielementti?
  "Raporttielementit ovat soluja, joissa on muutakin, kuin pelkkkä arvo.
  Käytetään erityisesti virheiden osoittamiseen (puuttuvat indeksit), mutta
  muitakin käyttötapauksia voi olla."
  [solu]
  (and (vector? solu)
       (> (count solu) 1)
       (keyword? (first solu))))

(defn excel-kaava?
  [solu]
  (and (raporttielementti? solu)
    (= :kaava (first solu))))

(defn sarakkeessa-raporttielementteja? [sarakkeen-indeksi taulukko-riveja]
  (let [sarakkeen-solut
        (mapcat
          (fn [rivi]
            (filter
              identity
              (map-indexed
                (fn [solun-indeksi solu] (when (= solun-indeksi sarakkeen-indeksi) solu))
                ;; Rivi voi olla mäppi, jossa optioita ja :rivi avaimen alla vektori,
                ;; tai pelkkä vektori
                (or (:rivi rivi) rivi))))
          taulukko-riveja)]
    (some raporttielementti? sarakkeen-solut)))

(defn raporttielementti-formatterilla
  "Liittää raporttielementtiin mukaan formatterin. Olettaa, että raporttielementin
  toinen arvo on mäppi, johon formatointifunktion voi liittää.
  Ei ylikirjoita formatteria, jos raporttielementille on määritelty oma :fmt avain."
  [solu fmt->fn fmt]
  (if (raporttielementti? solu)
    (if (map? (second solu))
      (assoc-in solu [1 :fmt]
                (let [fmt (or (get-in solu [1 :fmt]))]
                  (if fmt
                    (fmt->fn fmt)
                    str)))
      solu)

    ;; Jos annettu solu ei ole raporttielementti, voidaan sen arvo formatoida suoraan.
    (if fmt
      ((fmt->fn fmt) solu)
      solu)))

(defn formatoi-solu?
  [solu]
  (if (raporttielementti? solu)
    (case (get-in solu [1 :fmt?])
      true true
      false false
      true)

    true))

(defn voi-nahda-laajemman-kontekstin-raportit? [kayttaja]
  (and (not (roolit/roolissa? roolit/tilaajan-laadunvalvontakonsultti kayttaja))
       (roolit/tilaajan-kayttaja? kayttaja)))

#?(:clj
(defn tee-solu [solu arvo tyyli]
  (excel/set-cell! solu arvo)
  (excel/set-cell-style! solu tyyli)))

(defn hoitokausi-kuukausi-laskutus-otsikot [sarakkeet]
  ;; Palauttaa laskutusotsikkoja laskutusraporttiin
  (remove nil? (map (fn [sarake]
                      (let [otsikko (second (concat (first sarake)))]
                        (when (> (count otsikko) 1) otsikko))) sarakkeet)))

(defn hoitokausi-kuukausi-arvot [tiedot decimal?]
  ;; Palauttaa laskutusarvot laskutusraporttiin
  (doall (remove nil?
                 (mapcat (fn [x]
                           (map (fn [y]
                                  (let [arvo (:arvo (second y))
                                        arvo (if (= arvo 0) 0.0M arvo)
                                        koko (if (decimal? arvo) 1 (count arvo))]
                                    (if (> koko 0) arvo nil))) x)) tiedot))))

#?(:cljs
   (defn nykyinen-kayttaja-voi-nahda-laajemman-kontekstin-raportit? []
     (voi-nahda-laajemman-kontekstin-raportit? @harja.tiedot.istunto/kayttaja)))

(defn yrita
  "Yrittää tehdä ajaa funktion parametreineen. Jos heitetään poikkeus,
  palautetaan ensimmäinen parametri. Käytetään erityisesti formattereiden kanssa."
  [fn arvo & args]
  (try
    (apply (partial fn arvo) args)
    #?(:cljs (catch js/Object _ arvo))
    #?(:clj (catch Exception _ arvo))
    #?(:clj (catch Error _ arvo))))

(defn numero-fmt? [fmt]
  (boolean (#{:kokonaisluku :numero :numero-3desim :prosentti :prosentti-0desim :raha} fmt)))

(def +mahdolliset-roolit+
  [[nil "Kaikki"]
   [:urakanvalvoja "Urakanvalvoja"]
   [:ely-kayttaja "ELY:n käyttajä"]
   [:ely-paakayttaja "ELY:n paakayttaja"]
   [:jvh "Järjestelmävastaava"]
   [:rakennuttajakonsultti "Rakennuttajakonsultti"]
   [:urak-vastuuhenkilo "Urakoitsijan vastuuhenkilö"]
   [:urak-paakayttaja "Urakoitsijan pääkäyttäjä"]])

(def +mahdolliset-roolit-avaimet+
  (keep #(first %) +mahdolliset-roolit+))

(defn roolin-avain->nimi [avain]
  (second
    (first (filter #(= avain (first %))
                   +mahdolliset-roolit+))))
