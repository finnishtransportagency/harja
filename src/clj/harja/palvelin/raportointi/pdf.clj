(ns harja.palvelin.raportointi.pdf
  "Raportoinnin elementtien renderöinti PDF:ksi"
  (:require [harja.tyokalut.xsl-fo :as fo]
            [clojure.string :as str]
            [harja.visualisointi :as vis]
            [taoensso.timbre :as log]
            [harja.ui.skeema :as skeema]
            [harja.fmt :as fmt]))

(def taulukon-fonttikoko "8pt")
(def otsikon-fonttikoko "10pt")

(def raportin-tehostevari "#0066cc")

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

(def ^:const +max-rivimaara+ 1000)

(defn cdata
  "Käsittele arvo puhtaana tekstinä."
  [arvo]
  (str "<![CDATA[" arvo "]]>"))

(defn taulukko-header [sarakkeet]
  [:fo:table-header
   [:fo:table-row
    (for [otsikko (map :otsikko sarakkeet)]
      [:fo:table-cell {:border "solid 0.1mm black" :background-color raportin-tehostevari
                       :color "#ffffff"
                       :font-weight "normal" :padding "1mm"}
       [:fo:block (cdata otsikko)]])]])

(defn taulukko-body [sarakkeet data {:keys [otsikko viimeinen-rivi-yhteenveto?
                                  korosta-rivit oikealle-tasattavat-kentat] :as optiot}]
  (let [rivien-maara (count data)
        viimeinen-rivi (last data)
        data (if (> (count data) +max-rivimaara+)
               (vec (concat (take +max-rivimaara+ data)
                            (when viimeinen-rivi-yhteenveto?
                              [viimeinen-rivi])))
               data)
        oikealle-tasattavat-kentat (or oikealle-tasattavat-kentat #{})]
    [:fo:table-body
     (when (empty? data)
       [:fo:table-row
        [:fo:table-cell {:padding                "1mm"
                         :font-weight "normal"
                         :number-columns-spanned (count sarakkeet)}
         [:fo:block {:space-after "0.5em"}]
         [:fo:block "Ei tietoja"]]])
     (for [i-rivi (range (count data))
           :let [rivi (or (nth data i-rivi) "")
                 [rivi optiot]
                 (if (map? rivi)
                   [(:rivi rivi) rivi]
                   [rivi {}])
                 lihavoi-rivi? (:lihavoi? optiot)]]
       (if-let [otsikko (:otsikko rivi)]
         [:fo:table-row
          [:fo:table-cell {:padding                "1mm"
                           :font-weight            "normal"
                           :background-color       "#e1e1e1"
                           :number-columns-spanned (count sarakkeet)}
           [:fo:block {:space-after "0.5em"}]
           [:fo:block (cdata otsikko)]]]
         (let [yhteenveto? (when (and viimeinen-rivi-yhteenveto?
                                      (= viimeinen-rivi rivi))
                             {:background-color "#fafafa"
                              :border           (str "solid 0.3mm " raportin-tehostevari)
                              :font-weight      "bold"})
               korosta? (when (some #(= i-rivi %) korosta-rivit)
                          {:background-color       "#ff9900"
                           :color "black"})
               lihavoi? (when lihavoi-rivi?
                          {:font-weight "bold"})]
           [:fo:table-row
            (for [i (range (count sarakkeet))
                  :let [arvo-datassa (nth rivi i)
                        fmt (case (:fmt (nth sarakkeet i))
                              :numero #(fmt/desimaaliluku-opt % 1 true)
                              :prosentti #(fmt/prosentti-opt %)
                              str)
                        naytettava-arvo (or (if (vector? arvo-datassa)
                                              (muodosta-pdf arvo-datassa)
                                              (fmt arvo-datassa))
                                            "")]]
              [:fo:table-cell (merge {:border     (str "solid 0.1mm " raportin-tehostevari) :padding "1mm"
                                      :font-weight "normal"
                                      :text-align (if (oikealle-tasattavat-kentat i)
                                                    "right"
                                                    "left")}
                                     yhteenveto?
                                     korosta?
                                     lihavoi?)
               (when korosta?
                 [:fo:block {:space-after "0.2em"}])
               [:fo:block (cdata (str naytettava-arvo))]])])))
     (when (> rivien-maara +max-rivimaara+)
       [:fo:table-row
        [:fo:table-cell {:padding "1mm"
                         :number-columns-spanned (count sarakkeet)}
         [:fo:block {:space-after "0.5em"}]
         [:fo:block (str "Taulukossa näytetään vain ensimmäiset " +max-rivimaara+ " rivia. "
                         "Tarkenna hakuehtoa. "
                         (when viimeinen-rivi-yhteenveto?
                           "Yhteenveto on laskettu kaikista riveistä"))]]])]))

(defmethod muodosta-pdf :taulukko [[_ {:keys [otsikko] :as optiot} sarakkeet data]]
  (let [sarakkeet (skeema/laske-sarakkeiden-leveys (keep identity sarakkeet))]
    [:fo:block {:space-before "1em" :font-size taulukon-fonttikoko :font-weight "bold"} otsikko
     [:fo:table {:border (str "solid 0.2mm " raportin-tehostevari)}
      (for [{:keys [leveys]} sarakkeet]
        [:fo:table-column {:column-width leveys}])
      (taulukko-header sarakkeet)
      (taulukko-body sarakkeet data optiot)]
     [:fo:block {:space-after "1em"}]]))

(defmethod muodosta-pdf :liitteet [liitteet]
  (count (second liitteet)))

(defmethod muodosta-pdf :otsikko [[_ teksti]]
  [:fo:block {:padding-top "5mm" :font-size otsikon-fonttikoko} teksti])

(defmethod muodosta-pdf :otsikko-kuin-pylvaissa [[_ teksti]]
  [:fo:block {:font-weight "bold"
              :margin-bottom "2mm"
              :margin-top "2mm"} teksti])


(defmethod muodosta-pdf :teksti [[_ teksti {:keys [vari]}]]
  [:fo:block {:color (when vari vari)
              :font-size otsikon-fonttikoko} teksti])

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
     pylvaat)]
   [:fo:block {:space-after "1em"}]])

(defmethod muodosta-pdf :yhteenveto [[_ otsikot-ja-arvot]]
  ;;[:yhteenveto [[otsikko1 arvo1] ... [otsikkoN arvoN]]] -> yhteenveto (kuten päällystysilmoituksen alla)
  [:fo:table {:font-size otsikon-fonttikoko}
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
    [:fo:table {:font-size otsikon-fonttikoko}
     [:fo:table-column {:column-width "40%"}]
     [:fo:table-column {:column-width "40%"}]
     [:fo:table-column {:column-width "20%"}]
     [:fo:table-body
      [:fo:table-row
       [:fo:table-cell [:fo:block {:font-weight "bold"} raportin-nimi]]
       [:fo:table-cell [:fo:block "Ajettu " nyt]]
       [:fo:table-cell {:text-align "end"}
        [:fo:block
         "Sivu " [:fo:page-number]
         ;;" / " [:fo:page-number-citation {:ref-id "raportti-loppu"}]
         ]]]]]))

(defmethod muodosta-pdf :raportti [[_ raportin-tunnistetiedot & sisalto]]
  ;; Muodosta header raportin-tunnistetiedoista!
  (apply fo/dokumentti {:orientation (or (:orientaatio raportin-tunnistetiedot) :portrait)
                        :header {:sisalto (luo-header (:nimi raportin-tunnistetiedot))}}
         (concat [;; Jos raportin tunnistetiedoissa on annettu :tietoja avaimella, näytetään ne alussa
                  (when-let [tiedot (:tietoja raportin-tunnistetiedot)]
                    [:fo:block {:padding "1mm 0" :border "solid 0.2mm black" :margin-bottom "2mm"}
                     (muodosta-pdf [:yhteenveto tiedot])])]
                 (keep identity
                       (mapcat #(when %
                                 (if (seq? %)
                                   (map muodosta-pdf %)
                                   [(muodosta-pdf %)]))
                               sisalto))
                 #_[[:fo:block {:id "raportti-loppu"}]])))
