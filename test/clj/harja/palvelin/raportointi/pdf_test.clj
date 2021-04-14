(ns harja.palvelin.raportointi.pdf-test
  "Raportoinnin PDF elementtien muodostamisen testit"
  (:require [harja.palvelin.raportointi.pdf :as pdf :refer [muodosta-pdf]]
            [clojure.zip :as zip]
            [clojure.test :refer [deftest is]]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [taoensso.timbre :as log]))


;; Testataan, että virheellisistä syötteistä tulee oikein poikkeukset

(deftest tuntematon-elementti-palauttaa-nil
  (is (nil? (muodosta-pdf [:foo "jotain_"]))))

(deftest ei-vektori-palauttaa-itsensa
  (is (= {:jotain :ihan-muuta} (muodosta-pdf {:jotain :ihan-muuta})))
  (is (= ["ei keyword" "jotain"] (muodosta-pdf ["ei keyword" "jotain"]))))

;; Testataan eri tyyppisten elementtien muodostus perustasolla

(deftest otsikko
  (is (= [:fo:block {:padding-top "5mm" :font-size "10pt"} "TÄMÄ ON OTSIKKO"]
         (muodosta-pdf [:otsikko "TÄMÄ ON OTSIKKO"]))))

(deftest teksti
  (is (= [:fo:block
          {:color nil
           :font-size "10pt"}
          "TEKSTIÄ"]
         (muodosta-pdf [:teksti "TEKSTIÄ"]))))

(deftest taulukko
  (let [fo (muodosta-pdf [:taulukko {:otsikko "Taulukko"}
                          [{:otsikko "Eka" :leveys "10%"}
                           {:otsikko "Toka" :leveys "60%"}
                           {:otsikko "Kolmas" :leveys "30%"}]
                          [["eka" "toka" "kolmas"]]])]
    ;; PENDING: tämä testaa *TODELLA* tarkkaan, että rakenne on tismalleen oikein
    ;; XSL-FO generointia on hankala testata muuten, koska ei voi lopputulos PDF:n
    ;; visuaalista rakennetta oikein assertoida.
    (is (= fo ` [:fo:block
                {:space-before "1em", :font-size "8pt", :font-weight "bold"}
                "Taulukko"
                [:fo:table
                 ([:fo:table-column {:column-width "10%"}]
                  [:fo:table-column {:column-width "60%"}]
                  [:fo:table-column {:column-width "30%"}])
                 [:fo:table-header
                  nil
                  [:fo:table-row
                   ([:fo:table-cell
                     {:border "solid 0.1mm black",
                      :background-color "#0066cc",
                      :color "#ffffff",
                      :font-weight "normal",
                      :padding "1mm"
                      :text-align "left"}
                     [:fo:block "<![CDATA[Eka]]>"]]
                    [:fo:table-cell
                     {:border "solid 0.1mm black",
                      :background-color "#0066cc",
                      :color "#ffffff",
                      :font-weight "normal",
                      :padding "1mm"
                      :text-align "left"}
                     [:fo:block "<![CDATA[Toka]]>"]]
                    [:fo:table-cell
                     {:border "solid 0.1mm black",
                      :background-color "#0066cc",
                      :color "#ffffff",
                      :font-weight "normal",
                      :padding "1mm"
                      :text-align "left"}
                     [:fo:block "<![CDATA[Kolmas]]>"]])]]
                 [:fo:table-body
                  nil
                  ([:fo:table-row
                    ([:fo:table-cell
                      {:border-bottom "solid 0.1mm #0066cc",
                       :border-right "solid 0.1mm #0066cc",
                       :border-left "solid 0.1mm #0066cc",
                       :padding "1mm",
                       :font-weight "normal",
                       :text-align "left"}
                      nil
                      [:fo:block "<![CDATA[eka]]>"]]
                     [:fo:table-cell
                      {:border-bottom "solid 0.1mm #0066cc",
                       :border-right "solid 0.1mm #0066cc",
                       :border-left "solid 0.1mm #0066cc",
                       :padding "1mm",
                       :font-weight "normal",
                       :text-align "left"}
                      nil
                      [:fo:block "<![CDATA[toka]]>"]]
                     [:fo:table-cell
                      {:border-bottom "solid 0.1mm #0066cc",
                       :border-right "solid 0.1mm #0066cc",
                       :border-left "solid 0.1mm #0066cc",
                       :padding "1mm",
                       :font-weight "normal",
                       :text-align "left"}
                      nil
                      [:fo:block "<![CDATA[kolmas]]>"]])])
                  nil]]
                [:fo:block {:space-after "1em"}]]))))

(deftest pylvaat
  (let [fo (muodosta-pdf [:pylvaat {:otsikko "Mun pylväät"}
                          [["Q1" 1560
                            "Q2" 4333
                            "Q3" 3700
                            "Q4" 2121]]])
        [_ _ [elt _ svg]] fo]
    (is (= :fo:block elt))
    (is (= \M (first svg)))))

(deftest yhteenveto
  (let [fo (muodosta-pdf [:yhteenveto [["otsikko" "arvo"]
                                       ["toinen juttu" 4242]]])
        [_ _  s1 s2 [_ [r1 r2]]] fo]
    (is (= s1 [:fo:table-column {:column-width "25%"}]))
    (is (= s2 [:fo:table-column {:column-width "75%"}]))
    (is (= r1 [:fo:table-row
               [:fo:table-cell [:fo:block {:text-align "right" :font-weight "bold"} "otsikko:"]]
               [:fo:table-cell [:fo:block {:margin-left "5mm"} "arvo"]]]))
    (is (= r2 [:fo:table-row
               [:fo:table-cell [:fo:block {:text-align "right" :font-weight "bold"} "toinen juttu:"]]
               [:fo:table-cell [:fo:block {:margin-left "5mm"} "4242"]]]))))


;; Testataan koko raportti, eli täysi XSL-FO dokumentin luonti ja siitä PDF:n generointi

(def +testiraportti+ [:raportti {:nimi "Testiraportti"
                                 :tietoja [["Urakka" "Rymättylän päällystys"]
                                           ["Aika" "15.7.2015 \u2014 30.9.2015"]]}
                      [:otsikko "Tämä on hieno raportti"]
                      [:teksti "Tässäpä on sitten kappale tekstiä, joka raportissa tulee. Tämähän voisi olla mitä vain, kuten vaikka lorem ipsum dolor sit amet."]
                      [:taulukko {}
                       [{:otsikko "Nimi" :leveys "50%"}
                        {:otsikko "Kappaleita" :leveys "15%"}
                        {:otsikko "Hinta" :leveys "15%"}
                        {:otsikko "Yhteensä" :leveys "20%"}]

                       [["Fine leather jacket" 2 199 (* 2 199)]
                        ["Log from blammo" 1 39 39]
                        ["Suristin" 10 25 250]]]

                      [:otsikko "Tähän taas väliotsikko"]
                      [:pylvaat {:otsikko "Kvartaalien luvut"}
                       [["Q1" 123]
                        ["Q2" 1500]
                        ["Q3" 1000]
                        ["Q4" 777]]]
                      [:yhteenveto [["PDF-generointi" "toimii"]
                                    ["XSL-FO" "hyvin"]]]])



(defn luo-raportti-pdf-bytes []
  (let [fo (muodosta-pdf +testiraportti+)
        ff (#'pdf-vienti/luo-fop-factory)]
    (with-open [out (java.io.ByteArrayOutputStream.)]
      (#'pdf-vienti/hiccup->pdf ff fo out)
      (.toByteArray out))))

(deftest luo-raportti-pdf
  (is (> (count (luo-raportti-pdf-bytes)) 6500) "Testiraportin koko on reilut 6500 tavua"))

;; Evaluoi tämä, jos haluat saada raportti PDF:n näytölle auki
#_(do (require '[clojure.java.io :as io])
    (require '[clojure.java.shell :as sh])
    (io/copy (luo-raportti-pdf-bytes)
             (java.io.File. "raportti.pdf"))
    (sh/sh "open" "raportti.pdf"))
