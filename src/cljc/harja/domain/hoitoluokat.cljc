(ns harja.domain.hoitoluokat
  "Määrittelee talvihoitoluokat ja soratien hoitoluokat"
  (:require [harja.pvm :as pvm]))

(def ei-talvihoitoluokkaa-nimi "Ei tiedossa")

(def ^{:doc "Talvihoitoluokat voimaan 1.10.2019 alkaen urakoissa. Tierekisteriin nämä tulivat voimaan jo 14.6.2018 ja AVA:n geometria-aineistoon 2.7.2018. Nimi kertoo käyttöliittymässä käytetyn
nimen. Numero on tierekisterin koodi luokalle. Jos hoitoluokkaa ei saada geometrioista
selvitettyä, se asetetaan arvoon 100. Arvolla NULL PSQL upsert ei toimisi, koska UNIQUE
constrait vertailussa NULL arvot eivät aiheuta koskaan konfliktia."}
talvihoitoluokat
  [{:nimi "IsE"  :numero 1 :numero-str "1"}
   {:nimi "Is"  :numero 2 :numero-str "2"}
   {:nimi "I"   :numero 3 :numero-str "3"}
   {:nimi "Ib"  :numero 4 :numero-str "4"}
   {:nimi "Ic" :numero 5 :numero-str "5"}
   {:nimi "II"  :numero 6 :numero-str "6"}
   {:nimi "III" :numero 7 :numero-str "7"}
   {:nimi "L"  :numero 8 :numero-str "8"} ; 1.10.2019 alkaen käyttöön kentällä. On jo tierekisterissä 14.6.2018 alkaen ja talvihoitoluokat-geom.aineistossa.
   {:nimi "K1"  :numero 9 :numero-str "9"}
   {:nimi "K2"  :numero 10 :numero-str "10"}
   {:nimi "Ei talvihoitoa"  :numero 11 :numero-str "11"}
   {:nimi ei-talvihoitoluokkaa-nimi :numero 100 :numero-str "100"}])

(def hoitoluokkavaihtoehdot-raporteille
  (filter #(and (not= (:numero %) 8)
                (not= (:numero %) 11)
                (not= (:numero %) 100)) talvihoitoluokat))

(defn haluttujen-hoitoluokkien-nimet-ja-numerot [hoitoluokan-numero-set]
  (conj
    (vec (filter #(hoitoluokan-numero-set (:numero %)) talvihoitoluokat))
    {:nimi   ei-talvihoitoluokkaa-nimi
     :numero nil}
    ;; Joissain kyselyissä palautetaan toteumille talvihoitoluokat.
    ;; Jos talvihoitoluokkaa ei ole, :numero on nil. Kun :numero on täälläkin
    ;; nil, saadaan nämä kaksi liitettyä yhteen.
    ))

(def ^{:doc "Mäppäys talvihoitoluokan numerosta sen nimeen."}
  talvihoitoluokan-nimi
  (into {} (map (juxt :numero :nimi)) talvihoitoluokat))

(def ^{:doc "Mäppäys talvihoitoluokan numerosta (stringinä) sen nimeen."}
talvihoitoluokan-nimi-str
  (into {} (map (juxt :numero-str :nimi)) talvihoitoluokat))

(def ^{:doc "Mäppäys talvihoitoluokan nimestä sen numeroon."}
  talvihoitoluokan-numero
  (into {} (map (juxt :nimi :numero)) talvihoitoluokat))

(def ^{:doc "Mahdolliset soratieluokat. Nimi kertoo käyttöliittymässä käytetyn nimen.
Numero on tierekisterin koodi luokalle."}
  soratieluokat
  [{:nimi "I" :kuvaus "I lk Vilkkaat soratiet" :numero 1}
   {:nimi "II" :kuvaus "II lk Perussoratiet" :numero 2}
   {:nimi "III" :kuvaus "III lk Vähäliikenteiset soratiet" :numero 3}])

(def ^{:doc "Mäppäys soratieluokan numerosta sen nimeen."}
  soratieluokan-nimi
  (into {} (map (juxt :numero :nimi)) soratieluokat))
