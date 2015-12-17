(ns harja.palvelin.raportointi.pdf
  "Raportoinnin elementtien renderöinti PDF:ksi"
  (:require [harja.tyokalut.xsl-fo :as fo]
            [clojure.string :as str]
            [harja.visualisointi :as vis]
            [taoensso.timbre :as log]))

(defmulti muodosta-pdf
  "Muodostaa PDF:n XSL-FO hiccupin annetulle raporttielementille.
  Dispatch tyypin mukaan (vektorin 1. elementti)."
  (fn [elementti]
    (assert (and (vector? elementti)
                 (> (count elementti) 1)
                 (keyword? (first elementti)))
            (str "Raporttielementin on oltava vektori, jonka 1. elementti on tyyppi ja muut sen sisältöä, sain: "
                 (pr-str elementti)))
    (first elementti)))

(defmethod muodosta-pdf :taulukko [[_ {:keys [otsikko viimeinen-rivi-yhteenveto?] :as optiot} sarakkeet data]]
  [:fo:block {} otsikko
   [:fo:table {:border "solid 0.2mm black"}
    (for [{:keys [otsikko leveys]} sarakkeet]
      [:fo:table-column {:column-width leveys}])
    [:fo:table-header
     [:fo:table-row
      (for [otsikko (map :otsikko sarakkeet)]
        [:fo:table-cell {:border "solid 0.1mm black" :background-color "#afafaf" :font-weight "bold" :padding "1mm"}
         [:fo:block otsikko]])]]
    [:fo:table-body
     (let [viimeinen-rivi (last data)]
       (for [rivi data]
         (if-let [otsikko (:otsikko rivi)]
           [:fo:table-row

            [:fo:table-cell {:padding "1mm"
                             :font-weight "bold"
                             :number-columns-spanned (count sarakkeet)}
             [:fo:block {:space-after "0.5em"}]
             [:fo:block otsikko]]]
           (let [korosta? (when (and viimeinen-rivi-yhteenveto?
                                     (= viimeinen-rivi rivi))
                            {:font-weight "bold"})
                 _ (log/debug "rivi" rivi)
                 _ (log/debug "data" data)]
             [:fo:table-row
              (for [i (range (count sarakkeet))
                    :let [arvo (nth rivi i)]]
                [:fo:table-cell (merge {:border "solid 0.1mm black" :padding "1mm"}
                                       korosta?)
                 (when korosta? [:fo:block {:space-after "0.5em"}])
                 [:fo:block (str arvo)]])]))))]]])


(defmethod muodosta-pdf :otsikko [[_ teksti]]
  [:fo:block {:padding-top "5mm" :font-size "16pt"} teksti])

(defmethod muodosta-pdf :otsikko-kuin-pylvaissa [[_ teksti]]
  [:fo:block {:font-weight "bold"
              :margin-bottom "2mm"
              :margin-top "2mm"} teksti])


(defmethod muodosta-pdf :teksti [[_ teksti {:keys [vari]}]]
  [:fo:block {:color (when vari vari)} teksti])

(defmethod muodosta-pdf :varoitusteksti [[_ teksti]]
  (muodosta-pdf [:teksti teksti {:vari "#dd0000"}]))

(defmethod muodosta-pdf :pylvaat [[_ {:keys [otsikko vari fmt piilota-arvo? legend]} pylvaat]]
  ;;[:pylvaat "Otsikko" [[pylvas1 korkeus1] ... [pylvasN korkeusN]]] -> bar chart svg
  (log/debug "muodosta pdf pylväät data" pylvaat)
  [:fo:block {:margin-top "1em"}
   [:fo:block {:font-weight "bold"} otsikko]
   [:fo:instream-foreign-object {:content-width "17cm" :content-height "10cm"}
    (vis/bars {:width         180
               :height        80
               ;; tarvitaanko erityyppisille rapsoille eri formatteri?
               :format-amount (or fmt str)
               :hide-value?   piilota-arvo?
               :margin-x 20
               :margin-y 20
               :value-font-size "4pt"
               :tick-font-size "3pt"
               :y-axis-font-size "4pt"
               :legend legend
               }
     pylvaat)]])

(defmethod muodosta-pdf :yhteenveto [[_ otsikot-ja-arvot]]
  ;;[:yhteenveto [[otsikko1 arvo1] ... [otsikkoN arvoN]]] -> yhteenveto (kuten päällystysilmoituksen alla)
  [:fo:table
   [:fo:table-column {:column-width "25%"}]
   [:fo:table-column {:column-width "75%"}]
   [:fo:table-body
    (for [[otsikko arvo] otsikot-ja-arvot]
      [:fo:table-row
       [:fo:table-cell
        [:fo:block {:text-align "right" :font-weight "bold"}
         (let [otsikko (str/trim (str otsikko))]
           (if (.endsWith otsikko ":")
             otsikko
             (str otsikko ":")))]]
       [:fo:table-cell
        [:fo:block {:margin-left "5mm"} (str arvo)]]])]])

(defn- luo-header [raportin-nimi]
  (let [nyt (.format (java.text.SimpleDateFormat. "dd.MM.yyyy HH:mm") (java.util.Date.))]
    [:fo:table
     [:fo:table-column {:column-width "40%"}]
     [:fo:table-column {:column-width "40%"}]
     [:fo:table-column {:column-width "20%"}]
     [:fo:table-body
      [:fo:table-row
       [:fo:table-cell [:fo:block raportin-nimi]]
       [:fo:table-cell [:fo:block "Ajettu " nyt]]
       [:fo:table-cell {:text-align "end"}
        [:fo:block
         "Sivu " [:fo:page-number] " / " [:fo:page-number-citation {:ref-id "raportti-loppu"}]]]]]]))
  
(defmethod muodosta-pdf :raportti [[_ raportin-tunnistetiedot & sisalto]]
  ;; Muodosta header raportin-tunnistetiedoista!
  (apply fo/dokumentti {:orientation (or (:orientaatio raportin-tunnistetiedot) :portrait)
                        :header {:sisalto (luo-header (:nimi raportin-tunnistetiedot))}}
         (concat [;; Jos raportin tunnistetiedoissa on annettu :tietoja avaimella, näytetään ne alussa
                  (when-let [tiedot (:tietoja raportin-tunnistetiedot)]
                    [:fo:block {:padding "2mm" :border "solid 0.2mm black" :margin-bottom "2mm"}
                     (muodosta-pdf [:yhteenveto tiedot])])]
                 (keep identity
                       (mapcat #(when %
                                 (if (seq? %)
                                   (map muodosta-pdf %)
                                   [(muodosta-pdf %)]))
                               sisalto))
                 [[:fo:block {:id "raportti-loppu"}]])))
