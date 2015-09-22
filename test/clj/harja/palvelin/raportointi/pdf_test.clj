(ns harja.palvelin.raportointi.pdf-test
  "Raportoinnin PDF elementtien muodostamisen testit"
  (:require [harja.palvelin.raportointi.pdf :refer [muodosta-pdf]]
            [clojure.zip :as zip]
            [clojure.test :refer [deftest is]]))


;; Testataan, että virheellisistä syötteistä tulee oikein poikkeukset

(deftest tuntematon-elementti-heittaa-poikkeuksen
  (is (thrown? IllegalArgumentException
               (muodosta-pdf [:foo "jotain_"]))))

(deftest ei-vektori-assertoi
  (is (thrown? AssertionError
               (muodosta-pdf {:jotain :ihan-muuta}))))

(deftest muoto-oikein
  (is (thrown? AssertionError
               (muodosta-pdf ["ei keyword" "jotain"]))))


;; Testataan eri tyyppisten elementtien muodostus perustasolla

(deftest otsikko
  (is (= [:fo:block {:font-size "16pt"} "TÄMÄ ON OTSIKKO"]
         (muodosta-pdf [:otsikko "TÄMÄ ON OTSIKKO"]))))

(deftest teksti
  (is (= [:fo:block {} "TEKSTIÄ"]
         (muodosta-pdf [:teksti "TEKSTIÄ"]))))

(deftest taulukko
  (let [fo (muodosta-pdf [:taulukko [{:otsikko "Eka" :leveys "10%"}
                                     {:otsikko "Toka" :leveys "60%"}
                                     {:otsikko "Kolmas" :leveys "30%"}]
                          [["eka" "toka" "kolmas"]]])
        [_ [s1 s2 s3] body] fo
        [_ [[_ [a1 a2 a3]]]] body]
    (is (= s1 [:fo:table-column {:column-width "10%"}]))
    (is (= s2 [:fo:table-column {:column-width "60%"}]))
    (is (= s3 [:fo:table-column {:column-width "30%"}]))
    
    (is (= a1 [:fo:table-cell [:fo:block "eka"]]))
    (is (= a2 [:fo:table-cell [:fo:block "toka"]]))
    (is (= a3 [:fo:table-cell [:fo:block "kolmas"]]))))

(deftest pylvaat
  (let [fo (muodosta-pdf [:pylvaat [["Q1" 1560
                                     "Q2" 4333
                                     "Q3" 3700
                                     "Q4" 2121]]])
        [elt _ svg] fo]
    (is (= :fo:instream-foreign-object elt))
    (is (= :svg (first svg)))))

(deftest yhteenveto
  (let [fo (muodosta-pdf [:yhteenveto [["otsikko" "arvo"]
                                       ["toinen juttu" 4242]]])
        _ (println fo)
        [_  s1 s2 [_ [r1 r2]]] fo]
    (is (= s1 [:fo:table-column {:column-width "25%"}]))
    (is (= s2 [:fo:table-column {:column-width "75%"}]))
    (is (= r1 [:fo:table-row
               [:fo:table-cell [:fo:block "otsikko"]]
               [:fo:table-cell [:fo:block "arvo"]]]))
    (is (= r2 [:fo:table-row
               [:fo:table-cell [:fo:block "toinen juttu"]]
               [:fo:table-cell [:fo:block "4242"]]]))))
            
          
;; Testataan koko raportti, eli täysi XSL-FO dokumentin luonti

(def +testiraportti+ [:raportti {}
                      [:otsikko "Tämä on hieno raportti"]
                      [:teksti "Tässäpä on sitten kappale tekstiä, joka raportissa tulee. Tämähän voisi olla mitä vain, kuten vaikka lorem ipsum dolor sit amet."]
                      [:taulukko [{:otsikko "Nimi" :leveys "50%"}
                                  {:otsikko "Kappaleita" :leveys "15%"}
                                  {:otsikko "Hinta" :leveys "15%"}
                                  {:otsikko "Yhteensä" :leveys "20%"}]

                       [["Fine leather jacket" 2 199 (* 2 199)]
                        ["Log from blammo" 1 39 39]
                        ["Suristin" 10 25 250]]]

                      [:otsikko "Tähän taas väliotsikko"]
                      
                      [:yhteenveto [["PDF-generointi" "toimii"]
                                    ["XSL-FO" "hyvin"]]]])


;; Lainattu https://gist.github.com/PetrGlad/5027188
;; Muuntaa hiccupin zipperiksi, josta on helpompi hakea asioita testeissä
(defn hiccup-zip
  "Returns a zipper for Hiccup forms, given a root form."
  [root]
  (let [children-pos #(if (map? (second %)) 2 1)] 
    (zip/zipper 
      vector?
      #(drop (children-pos %) %) ; get children
      #(into [] (concat (take (children-pos %1) %1) %2)) ; make new node
      root)))


(deftest raportti-muodostuu-oikein
  (let [fo (hiccup-zip (muodosta-pdf +testiraportti+))

        ;; Laskeudutan region-body elementtiin
        body (-> fo zip/down zip/right zip/down zip/down)]
    ;;(is (= :fo:region-body (first (zip/node body))))
    
    ))

              
              
   
;; Testataan että raportista, jossa on kaikkia elementtejä, saa muodostettua PDF:n oikein

