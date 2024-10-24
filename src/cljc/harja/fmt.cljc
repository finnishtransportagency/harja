(ns harja.fmt
  "Yleisiä apureita erityyppisen datan formatointiin."
  (:require [clojure.string :as str]
            [harja.pvm :as pvm]
            [harja.tyokalut.big :as big]
            [clojure.string :as s]
            #?(:cljs [goog.i18n.currencyCodeMap])
            #?(:cljs [goog.i18n.NumberFormatSymbols])
            #?(:cljs [goog.i18n.NumberFormatSymbols_fi_FI])
            #?(:cljs [goog.i18n.NumberFormat]))
  #?(:clj
     (:import (java.text NumberFormat)
              (java.util Locale)
              (java.math RoundingMode BigDecimal))))

#?(:cljs (def frontin-formatointivirheviestit #{"epäluku"}))

#?(:clj
   (Locale/setDefault (Locale. "fi" "FI")))

#?(:cljs
   (set! goog.i18n.NumberFormatSymbols goog.i18n.NumberFormatSymbols_fi_FI))

#?(:cljs
   (def euro-number-format (doto (goog.i18n.NumberFormat. (.-DECIMAL goog.i18n.NumberFormat/Format))
                             (.setShowTrailingZeros false)
                             (.setMinimumFractionDigits 2)
                             (.setMaximumFractionDigits 2))))


(defn formatoi-numero-tuhansittain
  "Palauttaa luvun tekstinä joka ryhmitelty tuhansittain"
  [numero]
  #?(:cljs
     (.toLocaleString (js/Number. numero))
     :clj
     (.format (java.text.DecimalFormat. "#,###") numero)))


(defn euro
  "Formatoi summan euroina näyttämistä varten. Tuhaterottimien ja valinnaisen euromerkin kanssa."
  ([eur] (euro true eur))
  ([nayta-euromerkki eur]
   (if (big/big? eur)
     (str (big/fmt-full eur 2)
          (when nayta-euromerkki " \u20AC"))
     #?(:cljs
        ;; NOTE: lisätään itse perään euro symboli, koska googlella oli jotain ihan sotkua.
        ;; Käytetään googlen formatointia, koska toLocaleString tukee tarvittavia optioita, mutta
        ;; vasta IE11 versiosta lähtien.
        (let [tulos (.format euro-number-format eur)]
          (if (or
                (or (nil? eur) (and (string? eur) (empty? eur)))
                (frontin-formatointivirheviestit tulos))
            (throw (js/Error. (str "Arvoa ei voi formatoida euroksi: " (pr-str eur))))
            (if nayta-euromerkki (str tulos " \u20AC") tulos)))

        :clj
        (s/replace (.format
                     (doto
                         (if nayta-euromerkki
                           (NumberFormat/getCurrencyInstance)
                           (NumberFormat/getNumberInstance))
                       (.setMaximumFractionDigits 2)
                       (.setMinimumFractionDigits 2)
                       (.setNegativePrefix "\u002D")) eur) ;; Korvataan "-" sen unicodella. Aiheutti ongelmia pdf-raporteissa.
          #" €" " €")))))



(defn euro-opt
  "Formatoi euromäärän tai tyhjä, jos nil."
  ([summa] (euro-opt true summa))
  ([nayta-euromerkki summa]
   (if (or (nil? summa) (and (string? summa) (empty? summa)))
     ""
     (euro nayta-euromerkki summa))))

#?(:clj
   (defn formatoi-arvo-raportille [arvo]
     (as-> arvo arvo
           (clojure.core/bigdec arvo)
           (. arvo setScale 2 RoundingMode/HALF_UP))))

(defn yksikolla
  "Lisää arvo-merkkijonon loppuun välilyönnin ja yksikkö-merkkijonon"
  [yksikko arvo]
  (str arvo " " yksikko))

(defn yksikolla-opt
  "Lisää arvo-merkkijonon loppuun välilyönnin ja yksikkö-merkkijonon.
  Jos arvo on nil, palauttaa nil"
  [yksikko arvo]
  (when arvo (yksikolla yksikko arvo)))

(defn lyhenna-keskelta
  "Lyhentää tekstijonon haluttuun pituuteen siten, että
  pituutta otetaan pois keskeltä, ja korvataan kahdella pisteellä .."
  [haluttu-pituus teksti]
  (if (>= haluttu-pituus (count teksti))
    teksti

    (let [patkat (split-at (/ (count teksti) 2) teksti)
          eka (apply str (first patkat))
          ;; Ekan pituus pyöristetään ylöspäin, tokan alaspäin
          eka-haluttu-pituus (int (Math/ceil (/ haluttu-pituus 2)))
          toka (apply str (second patkat))
          toka-haluttu-pituus (int (Math/floor (/ haluttu-pituus 2)))]
      (str
        ;; Otetaan haluttu pituus -1, jotta pisteet mahtuu mukaan
        (apply str (take (dec eka-haluttu-pituus) eka))
        ".."
        (apply str (take-last (dec toka-haluttu-pituus) toka))))))

(def urakan-nimen-oletuspituus 30)

(defn lyhennetty-urakan-nimi
  "Lyhentää urakan nimen haluttuun pituuteen, lyhentämällä
  aluksi tiettyjä sanoja (esim urakka -> ur.), ja jos nämä eivät
  auta, leikkaamalla keskeltä kirjaimia pois ja korvaamalla leikatut
  kirjaimet kahdella pisteellä .."
  ([nimi] (lyhennetty-urakan-nimi urakan-nimen-oletuspituus nimi))
  ([pituus nimi]
   (loop [nimi nimi]
     (if (>= pituus (count nimi))
       nimi

       ;; Tänne voi lisätä lisää korvattavia asioita
       ;; Päällimmäiseksi yleisemmät korjaukset,
       ;; viimeiseksi "last resort" tyyppiset ratkaisut
       (recur
         (cond
           ;; Leading whitespace pois (ei ikinä valmistu jostain syystä)
           ;; Jos keksii miten tämän saa toimimaan, niin kaikista missä korvataan jokin teksti
           ;; tyhjällä merkkijonolla,
           ;; voi ottaa loppuvälilyönnin pois. Eli esim "harja-sampo " -> "harja-sampo"
           ;(re-find #"^\s*" nimi)
           ;(s/replace nimi #"^\s*" "")

           (re-find #"maanteiden hoitourakka" nimi)
           (s/replace nimi #"maanteiden hoitourakka" "MHU")

           (re-find #"Maanteiden hoitourakka" nimi)
           (s/replace nimi #"Maanteiden hoitourakka" "MHU")

           ;; Tähän yksittäisiä pitkiä urakoita. Ei ehkä kovin tehokasta tai järkevää.
           (re-find #"^[0-9]\w+,? Päällystettyjen teiden ylläpito, " nimi)
           (s/replace nimi #"^[0-9]\w+,? Päällystettyjen teiden ylläpito," "Pääll.yp.")

           ;; numerot (Sampo ID) alusta pois
           (re-find #"^[0-9]\w+,?" nimi)
           (s/replace nimi #"^[0-9]\w+,?" "")

           ;; Ylimääräiset välilyönnit pois
           (re-find #"\s\s+" nimi)
           (s/replace nimi #"\s\s+" " ")

           ;; "  - " -> "-"
           ;; Täytyy etsiä nämä kaksi erikseen, koska
           ;; \s*-\s* osuisi myös korjattuun "-" merkkijonoon,
           ;; ja "\s+-\s+" osuisi vain jos molemmilla puolilla on välilyönti.
           (or (re-find #"\s+-" nimi) (re-find #"-\s+" nimi))
           (s/replace nimi #"\s*-\s*" "-")

           ;; (?i) case insensitive ei toimi s/replacessa
           ;; cljs puolella. Olisi mahdollista käyttää vain
           ;; clj puolella käyttäen reader conditionaleja, mutta
           ;; samapa se on toistaa kaikki näin.

           ;; Nämä voinee ottaa pois kunhan käytössä on oikeaa oikeaa dataa
           (re-find #"testiurakka" nimi)
           (s/replace nimi #"testiurakka" "u.")

           (re-find #"Testiurakka" nimi)
           (s/replace nimi #"Testiurakka" "u.")

           (re-find #"TESTIURAKKA" nimi)
           (s/replace nimi #"TESTIURAKKA" "u.")

           (re-find #"harja-sampo " nimi)
           (s/replace nimi #"harja-sampo " "")

           (re-find #"Harja-Sampo " nimi)
           (s/replace nimi #"Harja-Sampo " "")

           (re-find #"HARJA-SAMPO " nimi)
           (s/replace nimi #"HARJA-SAMPO " "")

           ;; Yleisiä leikkauksia
           (re-find #"alueurakka" nimi)
           (s/replace nimi #"alueurakka" "au.")

           (re-find #"Alueurakka" nimi)
           (s/replace nimi #"Alueurakka" "au.")

           (re-find #"ALUEURAKKA" nimi)
           (s/replace nimi #"ALUEURAKKA" "au.")

           (re-find #"urakka" nimi)
           (s/replace nimi #"urakka" "u.")

           (re-find #"Urakka" nimi)
           (s/replace nimi #"Urakka" "u.")

           (re-find #"URAKKA" nimi)
           (s/replace nimi #"URAKKA" "u.")

           (re-find #"palvelusopimus" nimi)
           (s/replace nimi #"palvelusopimus" "ps.")

           (re-find #"Palvelusopimus" nimi)
           (s/replace nimi #"Palvelusopimus" "ps.")

           (re-find #"PALVELUSOPIMUS" nimi)
           (s/replace nimi #"PALVELUSOPIMUS" "ps.")

           (re-find #"hankintakustannukset" nimi)
           (s/replace nimi #"hankintakustannukset" "hk.")

           (re-find #"Hankintakustannukset" nimi)
           (s/replace nimi #"Hankintakustannukset" "hk.")

           (re-find #"HANKINTAKUSTANNUKSET" nimi)
           (s/replace nimi #"HANKINTAKUSTANNUKSET" "hk.")

           ;; Turhat POP ja ELY pois
           (re-find #"POP " nimi)
           (s/replace nimi #"POP " "")

           (re-find #"ELY " nimi)
           (s/replace nimi #"ELY " "")

           ;; Leikataan redundantti hallintayksikön nimi pois
           (re-find #"Uusimaa " nimi)
           (s/replace nimi #"Uusimaa " "")

           (re-find #"Varsinais-Suomi " nimi)
           (s/replace nimi #"Varsinais-Suomi " "")

           (re-find #"Kaakkois-Suomi " nimi)
           (s/replace nimi #"Kaakkois-Suomi " "")

           (re-find #"KAS " nimi)
           (s/replace nimi #"KAS " "")

           (re-find #"Pirkanmaa " nimi)
           (s/replace nimi #"Pirkanmaa " "")

           (re-find #"Pohjois-Savo " nimi)
           (s/replace nimi #"Pohjois-Savo " "")

           (re-find #"Keski-Suomi " nimi)
           (s/replace nimi #"Keski-Suomi " "")

           (re-find #"Etelä-Pohjanmaa " nimi)
           (s/replace nimi #"Etelä-Pohjanmaa " "")

           (re-find #"Pohjois-Pohjanmaa " nimi)
           (s/replace nimi #"Pohjois-Pohjanmaa " "")

           (re-find #"Lappi " nimi)
           (s/replace nimi #"Lappi " "")

           ;; Firmojen nimiin lyhennyksiä
           (re-find #"NCC Industry Oy" nimi)
           (s/replace nimi #"NCC Industry Oy" "NCC")

           ;; Kunnossapidon
           (re-find #"kunnossapidon" nimi)
           (s/replace nimi #"kunnossapidon" "kp.")

           (re-find #"Kunnossapidon" nimi)
           (s/replace nimi #"Kunnossapidon" "kp.")

           (re-find #"KUNNOSSAPIDON" nimi)
           (s/replace nimi #"KUNNOSSAPIDON" "kp.")

           ;; Ylläpidon
           (re-find #"ylläpidon" nimi)
           (s/replace nimi #"ylläpidon" "yp.")

           (re-find #"Ylläpidon" nimi)
           (s/replace nimi #"Ylläpidon" "yp.")

           (re-find #"YLLÄPIDON" nimi)
           (s/replace nimi #"YLLÄPIDON" "yp.")

           ;; Päällystys
           (re-find #"tienpäällystys" nimi)
           (s/replace nimi #"tienpäällystys" "pääl.")

           (re-find #"Tienpäällystys" nimi)
           (s/replace nimi #"Tienpäällystys" "pääl.")

           (re-find #"TIENPÄÄLLYSTYS" nimi)
           (s/replace nimi #"TIENPÄÄLLYSTYS" "pääl.")

           (re-find #"Päällystettyjen teiden ylläpito" nimi)
           (s/replace nimi #"Päällystettyjen" "Pääll.yp.")

           (re-find #"päällystettyjen" nimi)
           (s/replace nimi #"päällystettyjen" "pääll.")

           (re-find #"Päällystettyjen" nimi)
           (s/replace nimi #"Päällystettyjen" "Pääll.")

           (re-find #"päällystys" nimi)
           (s/replace nimi #"päällystys" "pääl.")

           (re-find #"Päällystys" nimi)
           (s/replace nimi #"Päällystys" "pääl.")

           (re-find #"PÄÄLLYSTYS" nimi)
           (s/replace nimi #"PÄÄLLYSTYS" "pääl.")

           (re-find #"päällysteiden" nimi)
           (s/replace nimi #"päällysteiden" "pääl.")

           (re-find #"Päällysteiden" nimi)
           (s/replace nimi #"Päällysteiden" "pääl.")

           (re-find #"PÄÄLLYSTEIDEN" nimi)
           (s/replace nimi #"PÄÄLLYSTEIDEN" "pääl.")

           (re-find #"päällystyksen" nimi)
           (s/replace nimi #"päällystyksen" "pääl.")

           (re-find #"Päällystyksen" nimi)
           (s/replace nimi #"Päällystyksen" "pääl.")

           (re-find #"PÄÄLLYSTYKSEN" nimi)
           (s/replace nimi #"PÄÄLLYSTYKSEN" "pääl.")

           ;; Paikkaus
           (re-find #"paikkaus" nimi)
           (s/replace nimi #"paikkaus" "paik.")

           (re-find #"Paikkaus" nimi)
           (s/replace nimi #"Paikkaus" "paik.")

           (re-find #"PAIKKAUS" nimi)
           (s/replace nimi #"PAIKKAUS" "paik.")

           (re-find #"paikkauksen" nimi)
           (s/replace nimi #"paikkauksen" "paik.")

           (re-find #"Paikkauksen" nimi)
           (s/replace nimi #"Paikkauksen" "paik.")

           (re-find #"PAIKKAUKSEN" nimi)
           (s/replace nimi #"PAIKKAUKSEN" "paik.")

           ;; Valaistus
           (re-find #"tievalaistuksen" nimi)
           (s/replace nimi #"tievalaistuksen" "tieval.")

           (re-find #"Tievalaistuksen" nimi)
           (s/replace nimi #"Tievalaistuksen" "Tieval.")

           (re-find #"valaistuksen" nimi)
           (s/replace nimi #"valaistuksen" "val.")

           (re-find #"Valaistuksen" nimi)
           (s/replace nimi #"Valaistuksen" "Val.")

           (re-find #"VALAISTUKSEN" nimi)
           (s/replace nimi #"VALAISTUKSEN" "val.")

           (re-find #"valaistus" nimi)
           (s/replace nimi #"valaistus" "val.")

           (re-find #"Valaistus" nimi)
           (s/replace nimi #"Valaistus" "val.")

           (re-find #"VALAISTUS" nimi)
           (s/replace nimi #"VALAISTUS" "val.")

           ;; Tiemerkintä
           (re-find #"merkinnän" nimi)
           (s/replace nimi #"merkinnän" "m.")

           (re-find #"Merkinnän" nimi)
           (s/replace nimi #"Merkinnän" "m.")

           (re-find #"MERKINNÄN" nimi)
           (s/replace nimi #"MERKINNÄN" "m.")

           (re-find #"merkintä" nimi)
           (s/replace nimi #"merkintä" "m.")

           (re-find #"Merkintä" nimi)
           (s/replace nimi #"Merkintä" "m.")

           (re-find #"MERKINTÄ" nimi)
           (s/replace nimi #"MERKINTÄ" "m.")

           (re-find #"merkintöjen" nimi)
           (s/replace nimi #"merkintöjen" "m.")

           (re-find #"Merkintöjen" nimi)
           (s/replace nimi #"Merkintöjen" "m.")

           (re-find #"MERKINTÖJEN" nimi)
           (s/replace nimi #"MERKINTÖJEN" "m.")

           ;; ", " -> " "
           (re-find #"\s*,\s*" nimi)
           (s/replace nimi #"\s*,\s*" " ")

           :else (lyhenna-keskelta pituus nimi)))))))

(defn lyhennetty-urakan-nimi-opt
  ([nimi] (lyhennetty-urakan-nimi-opt urakan-nimen-oletuspituus nimi))
  ([nimi pituus]
   (when nimi (lyhennetty-urakan-nimi pituus nimi))))

(def roomalaisena-numerona {1 "I"
                            2 "II"
                            3 "III"})

(defn euro-ei-voitu-laskea
  "Formatoi euromäärän tai sanoo ei voitu laskea, jos nil."
  [summa]
  (if summa
    (euro false summa)
    "Ei voitu laskea"))

(defn pikseleina
  [arvo]
  (str arvo "px"))

(defn asteina [arvo]
  (str arvo " \u2103"))

(defn kayttaja
  "Formatoi käyttäjän nimen."
  [{:keys [etunimi sukunimi]}]
  (str etunimi " " sukunimi))

(defn kayttaja-opt
  "Formatoi käyttäjän nimen tai tyhjä, jos nil."
  [k]
  (if k
    (kayttaja k)
    ""))

(defn pvm
  "Formatoi päivämärään"
  [pvm]
  (pvm/pvm pvm))

(defn pvm-opt
  "Formatoi päivämäärän tai tyhjä, jos nil.
  Jos formatoitava arvo ei ole pvm, palauttaa itse arvon."
  [p]
  (if p
    (pvm p)
    ""))

(defn pvm-vali
  "Näyttää päivämäärävälin joka vuoden kera tai ilman ihmisluettavassa muodossa."
  ([[alku loppu]]
   (pvm-vali [alku loppu] true))
  ([[alku loppu] nayta-vuosi?]
   (str (pvm/pvm alku {:nayta-vuosi-fn (constantly nayta-vuosi?)})
        " \u2014 "
        (pvm/pvm loppu {:nayta-vuosi-fn (constantly nayta-vuosi?)}))))

(defn pvm-vali-opt [vali]
  (if vali
    (pvm-vali vali)
    ""))

(defn hoitokauden-jarjestysluku-ja-vuodet
  "Näyttää hoitokauden esim 1.10.2022 - 30.9.2023 formaatissa '2. hoitovuosi (2022 — 2023)'.
  Olettaa saavansa parametrit joko kaikki päivämäärinä tai kaikki vuosina:
  valittu-hk: [pp.kk.vvvv pp.kk.vvvv] TAI vvvv tai järjestysnumero, esim. 2
  hoitovuodet: [[pp.kk.vvvv pp.kk.vvvv]...] TAI [2012 2013 2014 2015 2016] TAI järjestysnumerot, esim. [1 2 3 4 5)"
  [valittu-hk hoitovuodet vuosi-termi]
  (assert (or (and (int? valittu-hk) (every? int? hoitovuodet))
              (and (int? valittu-hk) (> valittu-hk 0) (< valittu-hk 10) (every? vector? hoitovuodet))
              (and (vector? valittu-hk) (every? vector? hoitovuodet))) "Kaikkien parametrien on oltava joko numeroita tai pvm:n sisältäviä vektoreita. Myös hoitokauden järjestysluku ja vektori ")
  (when (and (int? valittu-hk) (< valittu-hk 10) (every? vector? hoitovuodet))
    (assert (<= valittu-hk (count hoitovuodet)) "Indeksi yli rajojen - osoitin suurempi kuin hoitovuosien lukumäärä. "))

  (let [monesko (first (keep-indexed (fn [i hk]
                                       (if (and (int? valittu-hk)
                                                (< valittu-hk 10))
                                         valittu-hk
                                         (when (= hk valittu-hk)
                                          (inc (int i)))))
                                     hoitovuodet))
        hk-fmt #(cond
                  ;; valittu-hk on vuosiluku
                  (and (int? %) (> % 2000))
                  (str % "\u2014" (inc %))

                  ;; valittu-hk on järjestysluku, esim kustannussuunnitelmassa, eli 1, 2, 3, ...
                  (and (int? %) (< % 10) (get hoitovuodet (dec %)))
                  (str (pvm/vuosi (first (get hoitovuodet (dec %)))) "\u2014"
                       (pvm/vuosi (second (get hoitovuodet (dec %)))))

                  :else ;; valittu-hk on pvm
                  (let [alkuvuosi (pvm/vuosi (first %))
                        loppuvuosi (pvm/vuosi (second %))]
                    ;; jos alku- ja loppuvuosi ovat samat, ei toisteta samaa vuosilukua
                    (str alkuvuosi (when-not (= alkuvuosi loppuvuosi)
                                     (str "\u2014" loppuvuosi)))))]
    (str (when monesko (str monesko ". ")) (str/lower-case vuosi-termi) " (" (hk-fmt valittu-hk) ")")))

#?(:cljs
   (def desimaali-fmt
     (memoize (fn [min-desimaalit max-desimaalit]
                (doto (goog.i18n.NumberFormat.
                        (.-DECIMAL goog.i18n.NumberFormat/Format))
                  (.setShowTrailingZeros false)
                  (.setMinimumFractionDigits min-desimaalit)
                  (.setMaximumFractionDigits max-desimaalit))))))

(def oletus-max-desimaalit
  "Käytetään maksimitarkkuutena, jos ei määritelty.
   goog.i18n.NumberFormat oletus on null
   java.text.DecimalFormat oletus on 3 (ei voida asettaa nulliksi)"
  10)

#?(:clj (def desimaali-symbolit
          (doto (java.text.DecimalFormatSymbols.)
            (.setGroupingSeparator \ ))))

(defn desimaaliluku
  ([luku] (desimaaliluku luku 2 false))
  ([luku tarkkuus] (desimaaliluku luku tarkkuus false))
  ([luku tarkkuus ryhmitelty?] (desimaaliluku luku tarkkuus tarkkuus ryhmitelty?))
  ([luku min-desimaalit max-desimaalit ryhmitelty?]
   "Formatoi desimaaliluku.
   luku: formatoitava luku
   min-desimaalit: monenko desimaalin tarkkuudella luku vähintään näytetään (voi olla nil)
   max-desimaalit: monenko desimaalin tarkkuudella luku korkeintaan näytetään (jos nil, käytetään arvoa oletus-max-desimaalit)
   ryhmitelty?: ryhmitelläänkö kokonaislukuosa (esim. 123 000 000,00)

   Esimerkkejä:
   (desimaaliluku 123 nil nil false)
   => \"123\"
   (desimaaliluku 123 2 3 false)
   => \"123,00\"
   (desimaaliluku 123.123456789 nil nil false)
   => \"123,123456789\"
   (desimaaliluku 123.123456789 nil 7 false)
   => \"123,1234568\"

   HUOM: ainakin js-puolella formatoidun luvun loppuun tulee virhettä, jos luvussa on riittävästi
   merkitseviä numeroita.
   Esim. jos max-desimaalit on 7, niin kokonaislukuosalle voi sallia vain 9 numeroa:

   (desimaaliluku 777777777.1234567 nil 7 false)
   => \"777777777,1234567\" ; oikein
   (desimaaliluku 7777777777.1234567 nil 7 false)
   => \"7777777777,1234576\" ; väärin

   Jos tarvitaan paljon merkitseviä numeroita, niin kannattaa harkita big decimalin käyttämistä."
   #?(:cljs
      ; Jostain syystä ei voi formatoida desimaalilukua nollalla desimaalilla. Aiheuttaa poikkeuksen.
      (if (= max-desimaalit 0)
        (.toFixed luku 0)
        (let [max-desimaalit (or max-desimaalit oletus-max-desimaalit)
              fmt (desimaali-fmt min-desimaalit max-desimaalit)
              formatoitu (.format fmt luku)]
          (cond
            (or
              (or (nil? luku) (and (string? luku) (empty? luku)))
              (frontin-formatointivirheviestit formatoitu))
            (do
              (println "Lukua ei voitu formatoida. Todennäköisesti sinun pitää muuttaa kentän " luku " tyyppi pois numerosta.")
              luku)

            ryhmitelty?
            formatoitu

            :default
            ;; Poista whitespace ja non-breaking space
            (s/replace formatoitu #"[\s\u00A0]" ""))))
      :clj
      (let [max-desimaalit (or max-desimaalit oletus-max-desimaalit)
            fmt (doto (java.text.DecimalFormat.)
                  (.setDecimalFormatSymbols desimaali-symbolit)
                  (.setGroupingSize (if ryhmitelty? 3 0))
                  (.setMaximumFractionDigits max-desimaalit))]
        (when min-desimaalit
          (.setMinimumFractionDigits fmt min-desimaalit))
        (.format fmt (double luku))))))

(defn desimaaliluku-opt
  ([luku] (desimaaliluku-opt luku 2 false))
  ([luku tarkkuus] (desimaaliluku-opt luku tarkkuus false))
  ([luku tarkkuus ryhmitelty?]
   (desimaaliluku-opt luku tarkkuus tarkkuus ryhmitelty?))
  ([luku min-desimaalit max-desimaalit ryhmitelty?]
   (if (or (nil? luku) (and (string? luku) (empty? luku)))
     ""
     (desimaaliluku luku min-desimaalit max-desimaalit ryhmitelty?))))

(defn piste->pilkku [luku]
  (if luku
    (clojure.string/replace (str luku) "." ",")
    luku))

(defn pilkku->piste [luku]
  (if luku
    (clojure.string/replace (str luku) "," ".")
    luku))

(defn kokonaisluku-opt
  [luku]
  (if luku
    (if (number? luku)
      (int luku)
      #?(:cljs (js/parseInt luku)
         :clj  (Integer/parseInt luku)))
    ""))

(defn pyorista-ehka-kolmeen [arvo]
  (let [desimaalit-seq (s/split (str arvo) #"\.")
        desimaalit (if (> (count desimaalit-seq) 1)
                     (count (second desimaalit-seq))
                     0)
        arvo (try
               (if (> desimaalit 2)
                 (desimaaliluku-opt arvo 3 true)
                 (desimaaliluku-opt arvo 2 true))
               #?(:cljs (catch js/Object _ arvo))
               #?(:clj (catch Exception _ arvo)))]
    arvo))

(defn trimmaa-normaali-luku
  "Monet formatointifunktiot formatoivat luvut ikään kuin ne olisivat rahoja. Eli kahteen desimaaliin.
  Tällä formatoinnilla muutetaan luvun piste '.' pilkuksi ja leikataan kolmannen desimaalin jälkeen ylimääräiset pois."
  [luku]
  (when luku
    (let [desimaalit (s/split (str luku) #"\.")
          desimaalien-maara (if (second desimaalit)
                              (count (second desimaalit))
                              0)]
      (if (> desimaalien-maara 3)
        (pyorista-ehka-kolmeen luku)
        (piste->pilkku luku)))))


(defn prosentti
  ([luku] (prosentti luku 1))
  ([luku tarkkuus]
   (str (desimaaliluku luku tarkkuus) "\u00A0%")))

(defn lampotila
  ([luku] (lampotila luku 1))
  ([luku tarkkuus]
   (str (desimaaliluku luku tarkkuus) " °C")))

(defn lampotila-opt
  ([luku] (lampotila-opt luku 1))
  ([luku tarkkuus]
   (if (or (nil? luku) (and (string? luku) (empty? luku)))
     ""
     (lampotila luku tarkkuus))))

(defn prosentti-opt
  ([luku] (prosentti-opt luku 1))
  ([luku tarkkuus]
   (if (or (nil? luku) (and (string? luku) (empty? luku)))
     ""
     (prosentti luku tarkkuus))))

(defn trimmaa-puhelinnumero
  "Ottaa suomalaisen puhelinnumeron teksimuodossa ja palauttaa sen yksinkertaistetussa numeromuodossa ilman etuliitettä
  Esim. +358400-123-456 -> 0400123456
        +358500123123 -> 0500123123
        0400-123123 -> 0400123123"
  [numero-string]
  (let [puhdas-numero (apply str (filter
                                   #(#{\0, \1, \2, \3, \4, \5, \6, \7, \8, \9, \+} %)
                                   numero-string))
        siivottu-etuliite (if (= (str (first puhdas-numero)) "+")
                            (str "0" (subs puhdas-numero 4 (count puhdas-numero)))
                            puhdas-numero)]
    siivottu-etuliite))

(defn pituus [metria]
  (if (< metria 1000)
    (str (desimaaliluku metria 0) " m")
    (str (desimaaliluku (/ metria 1000.0) 2) " km")))

(defn pituus-opt [metria]
  (if (nil? metria)
    ""
    (pituus metria)))

(defn luku-indeksikorotus
  "Formatoi luvun ilman yksikköä tai stringin 'Indeksi puuttuu', jos nil."
  [summa]
  (if summa
    (euro false summa)
    "Indeksi puuttuu"))

(defn totuus [arvo]
  (if arvo
    "Kyllä"
    "Ei"))

(defn leikkaa-merkkijono
  "Näyttää annetusta merkkijonosta korkeintaan pituuden määrän merkkejä.

  Optiot mappi, jossa voi olla arvot:
  pisteet?      Näyttää kolme pistettä tekstin lopussa jos teksti katkeaa. Oletus false."
  ([pituus merkkijono] (leikkaa-merkkijono pituus {} merkkijono))
  ([pituus {:keys [pisteet?] :as optiot} merkkijono]
   (when merkkijono
     (let [tulos (subs merkkijono 0 (min (count merkkijono) pituus))]
       (if (and pisteet? (> (count merkkijono) pituus))
         (str tulos "...")
         tulos)))))

(defn left-pad
  ([minimi-pituus sisalto] (left-pad " " minimi-pituus sisalto))
  ([merkki minimi-pituus sisalto]
   (let [merkkijono (str sisalto)]
     (str (apply str (repeat (- minimi-pituus (count merkkijono)) merkki)) merkkijono))))

(defn kuvaile-paivien-maara
  "Ottaa päivien määrää kuvaavan numeron, ja kuvailee sen tekstinä.
  - Jos päiviä on 0, palauttaa tyhjän stringin
  - Jos päiviä on alle 7, näytetään päivien määrä
  - Jos päiviä on alle kuukausi, näytetään määrä viikkoina (pyöristettynä alimpaan)
  - Jos päiviä on alle vuosi, näytetään määrä kuukausina (pyöristettynä alimpaan)
  - Muussa tapauksessa näytetään päivien määrä vuosina (pyöristettynä alimpaan)"
  ([paivat] (kuvaile-paivien-maara paivat {}))
  ([paivat {:keys [lyhenna-yksikot?] :as optiot}]
   (assert (and (number? paivat) (>= paivat 0)) "Ajan tulee olla 0 tai suurempi")
   (let [viikko 7
         kuukausi (* viikko 4)
         vuosi (* kuukausi 12)]
     (cond (= paivat 0)
           ""

           (< paivat viikko)
           (str paivat (if (= paivat 1)
                         (if lyhenna-yksikot? "pv" " päivä")
                         (if lyhenna-yksikot? "pv" " päivää")))

           (< paivat kuukausi)
           (let [viikot (int (/ paivat viikko))]
             (str viikot (if (= viikot 1)
                           (if lyhenna-yksikot? "vk" " viikko")
                           (if lyhenna-yksikot? "vk" " viikkoa"))))

           (< paivat vuosi)
           (let [kuukaudet (int (/ paivat kuukausi))]
             (str kuukaudet (if (= kuukaudet 1)
                              (if lyhenna-yksikot? "kk" " kuukausi")
                              (if lyhenna-yksikot? "kk" " kuukautta"))))

           (>= paivat vuosi)
           (let [vuodet (int (/ paivat vuosi))]
             (str vuodet (if (= vuodet 1)
                           (if lyhenna-yksikot? "v" " vuosi")
                           (if lyhenna-yksikot? "v" " vuotta"))))))))

(defn aika [{:keys [tunnit minuutit sekunnit]}]
  (let [p #(left-pad "0" 2 (str %))]
    (str (p tunnit) ":"
         (p minuutit)
         (when sekunnit
           (str ":" (p sekunnit))))))

(defn merkkijonon-alku [s max-pituus]
  (when (string? s)
    (let [lyhyt-s (subs s 0 (min (count s) max-pituus))]
      (if (= lyhyt-s s)
        s
        (str lyhyt-s "[...]")))))

(defn urakkatyyppi-fmt [urakkatyyppi]
  (case urakkatyyppi
    (:hoito :valaistus :paikkaus :siltakorjaus) (name urakkatyyppi)
    :paallystys "päällystys"
    :tiemerkinta  "tiemerkintä"
    :tekniset-laitteet "tekniset laitteet"

    "Ei vielä formatointia ko. urakkatyypille"))

(defn string-avaimeksi [s]
  (-> s
    str/lower-case
    (str/replace #"[\s]+" "-") keyword))
