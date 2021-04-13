(ns harja.palvelin.raportointi.pdf
  "Raportoinnin elementtien renderöinti PDF:ksi

  Harjan raportit ovat Clojuren tietorakenteita, joissa käytetään
  tiettyä rakennetta ja tiettyjä avainsanoja. Nämä raportit annetaan
  eteenpäin moottoreille, jotka luovat tietorakenteen pohjalta raportin.
  Tärkeä yksityiskohta on, että raporttien olisi tarkoitus sisältää ns.
  raakaa dataa, ja antaa raportin formatoida data oikeaan muotoon sarakkeen :fmt
  tiedon perusteella.

  Tärkein muodosta-pdf metodin toteutus on :taulukko. Mm. uusien saraketyyppien
  tukeminen lisätään sinne.
  "
  (:require [harja.tyokalut.xsl-fo :as fo]
            [clojure.string :as str]
            [harja.visualisointi :as vis]
            [taoensso.timbre :as log]
            [harja.ui.skeema :as skeema]
            [harja.fmt :as fmt]
            [harja.domain.raportointi :as raportti-domain]
            [harja.ui.aikajana :as aikajana]
            [harja.pvm :as pvm]
            [clj-time.coerce :as c]))

(def ^:dynamic *orientaatio* nil)

(def taulukon-fonttikoko 8)
(def taulukon-fonttikoko-yksikko "pt")
(def otsikon-fonttikoko "10pt")

(def raportin-tehostevari "#0066cc")

(defmulti muodosta-pdf
          "Muodostaa PDF:n XSL-FO hiccupin annetulle raporttielementille.
          Dispatch tyypin mukaan (vektorin 1. elementti)."
          (fn [elementti]
            (if (raportti-domain/raporttielementti? elementti)
              (first elementti)
              :vain-arvo)))

(def ^:const +max-rivimaara+ 1000)

(defn cdata
  "Käsittele arvo puhtaana tekstinä."
  [arvo]
  (str "<![CDATA[" arvo "]]>"))

(defn tasaus [tasaa]
  (case tasaa
    :oikea "right"
    :keskita "center"
    "left"))

(def reunan-tyyli (str "solid 0.1mm " raportin-tehostevari))

(defmethod muodosta-pdf :vain-arvo [arvo] arvo)

(defmethod muodosta-pdf :liitteet [liitteet]
  (count (second liitteet)))

(defmethod muodosta-pdf :arvo-ja-osuus [[_ {:keys [arvo osuus fmt]}]]
  [:fo:inline
   [:fo:inline (if fmt (fmt arvo) arvo)]
   [:fo:inline " "]
   [:fo:inline {:font-size (str (- taulukon-fonttikoko 2) taulukon-fonttikoko-yksikko)} (str "( " osuus "%)")]])

(defmethod muodosta-pdf :arvo-ja-yksikko [[_ {:keys [arvo yksikko fmt desimaalien-maara]}]]
  [:fo:inline
   [:fo:inline (cond
                 desimaalien-maara (fmt/desimaaliluku-opt arvo desimaalien-maara)
                 fmt (fmt arvo)
                 :else arvo)]
   [:fo:inline (str yksikko)]])

(defmethod muodosta-pdf :varillinen-teksti [[_ {:keys [arvo tyyli itsepaisesti-maaritelty-oma-vari fmt]}]]
  [:fo:inline
   [:fo:inline {:color (or itsepaisesti-maaritelty-oma-vari
                           (raportti-domain/virhetyylit tyyli)
                           "black")}
    ;; Try to fix VHAR-2391 replacing - with its unicode representation
    (str/replace
      (if fmt (fmt arvo) arvo)
      "-" "\u002D")]])


(def alareuna
  {:border-bottom reunan-tyyli})

(def oikea-reuna
  {:border-right reunan-tyyli})

(def vasen-reuna
  {:border-left reunan-tyyli})

(def ei-yla-tai-ala-reunoja
  {:border-top "none"
   :border-bottom "none"})

(defn- border-tyyli [{reunus :reunus}]
  (merge alareuna
         (case reunus
           ;; Reunus vain oikealle
           :oikea oikea-reuna

           ;; Reunus vain vasemmalle
           :vasen vasen-reuna

           ;; Ei lainkaan vaseanta eikä oikeaa reunusta
           :ei {}

           ;; Ei reunusmäärittelyä, tehdään oletus
           (merge oikea-reuna vasen-reuna))))

(defn- taulukko-valiotsikko [otsikko sarakkeet]
  [:fo:table-row
   [:fo:table-cell {:padding "1mm"
                    :font-weight "normal"
                    :background-color "#e1e1e1"
                    :number-columns-spanned (count sarakkeet)}
    [:fo:block {:space-after "0.5em"}]
    [:fo:block (cdata otsikko)]]])

(defn- formatoija-fmt-mukaan [fmt]
  (case fmt
    ;; Jos halutaan tukea erityyppisiä sarakkeita,
    ;; pitää tänne lisätä formatter.
    :numero #(raportti-domain/yrita fmt/desimaaliluku-opt % 1 true)
    :numero-3desim #(fmt/pyorista-ehka-kolmeen %)
    :prosentti-0desim #(raportti-domain/yrita fmt/prosentti-opt % 0)
    :prosentti #(raportti-domain/yrita fmt/prosentti-opt %)
    :raha #(raportti-domain/yrita fmt/euro-opt %)
    :pvm #(raportti-domain/yrita fmt/pvm-opt %)
    str))

(defn- taulukko-rivit [sarakkeet data viimeinen-rivi
                       {:keys [viimeinen-rivi-yhteenveto? korosta-rivit
                               oikealle-tasattavat-kentat] :as optiot}]
  (let [oikealle-tasattavat-kentat (or oikealle-tasattavat-kentat #{})]
    (for [i-rivi (range (count data))
          :let [rivi (or (nth data i-rivi) "")
                [rivi optiot]
                (if (map? rivi)
                  [(:rivi rivi) rivi]
                  [rivi {}])
                lihavoi-rivi? (:lihavoi? optiot)
                korosta-rivi? (:korosta? optiot)
                korosta-hennosti? (:korosta-hennosti? optiot)]]
      (if-let [otsikko (:otsikko optiot)]
        (taulukko-valiotsikko otsikko sarakkeet)
        (let [yhteenveto? (when (and viimeinen-rivi-yhteenveto?
                                     (= viimeinen-rivi rivi))
                            {:background-color "#fafafa"
                             :border (str "solid 0.3mm " raportin-tehostevari)
                             :font-weight "bold"})
              korosta? (when (or korosta-rivi? (some #(= i-rivi %) korosta-rivit))
                         {:background-color "#ff9900"
                          :color "black"})
              korosta-hennosti? (when korosta-hennosti?
                                  {:background-color "#dee7fb"
                                   :color "black"})
              lihavoi? (when lihavoi-rivi?
                         {:font-weight "bold"})]
          [:fo:table-row
           (for [i (range (count sarakkeet))
                 :let [arvo-datassa (nth rivi i)
                       ;; ui.yleiset/totuus-ikonin tuki toistaiseksi tämä
                       arvo-datassa (if (= [:span.livicon-check] arvo-datassa)
                                      "X"
                                      arvo-datassa)
                       sarake (nth sarakkeet i)
                       fmt (formatoija-fmt-mukaan (:fmt sarake))
                       naytettava-arvo (or
                                         (cond
                                           (raportti-domain/raporttielementti? arvo-datassa)
                                           (muodosta-pdf
                                             (if (raportti-domain/formatoi-solu? arvo-datassa)
                                               (raportti-domain/raporttielementti-formatterilla
                                                 arvo-datassa formatoija-fmt-mukaan (:fmt sarake))
                                               arvo-datassa))

                                           :else (fmt arvo-datassa))
                                         "")]]
             [:fo:table-cell (merge
                               (border-tyyli sarake)
                               {:padding "1mm"
                                :font-weight "normal"
                                :text-align (if (oikealle-tasattavat-kentat i)
                                              "right"
                                              (tasaus (:tasaa sarake)))}
                               yhteenveto?
                               korosta?
                               korosta-hennosti?
                               lihavoi?)
              (when korosta?
                [:fo:block {:space-after "0.2em"}])
              [:fo:block (if (string? naytettava-arvo)
                           (cdata (str naytettava-arvo))
                           naytettava-arvo)]])])))))

(defn- taulukko-alaosa [rivien-maara sarakkeet viimeinen-rivi-yhteenveto?]
  (when (> rivien-maara +max-rivimaara+)
    [:fo:table-row
     [:fo:table-cell {:padding "1mm"
                      :number-columns-spanned (count sarakkeet)}
      [:fo:block {:space-after "0.5em"}]
      [:fo:block (str "Taulukossa näytetään vain ensimmäiset " +max-rivimaara+ " rivia. "
                      "Tarkenna hakuehtoa. "
                      (when viimeinen-rivi-yhteenveto?
                        "Yhteenveto on laskettu kaikista riveistä"))]]]))

(defn taulukko-header [optiot sarakkeet]
  [:fo:table-header
   (when-let [rivi-ennen (:rivi-ennen optiot)]
     [:fo:table-row
      (for [{:keys [teksti sarakkeita tasaa]} rivi-ennen]
        [:fo:table-cell {:border reunan-tyyli :background-color raportin-tehostevari
                         :color "#ffffff"
                         :number-columns-spanned (or sarakkeita 1)
                         :text-align (tasaus tasaa)}
         [:fo:block teksti]])])

   [:fo:table-row
    (for [otsikko (map :otsikko sarakkeet)]
      [:fo:table-cell {:border "solid 0.1mm black" :background-color raportin-tehostevari
                       :color "#ffffff"
                       :font-weight "normal" :padding "1mm"}
       [:fo:block (cdata otsikko)]])]])

(defn taulukko-body [sarakkeet data {:keys [viimeinen-rivi-yhteenveto? tyhja] :as optiot}]
  (let [rivien-maara (count data)
        viimeinen-rivi (last data)
        data (if (> (count data) +max-rivimaara+)
               (vec (concat (take +max-rivimaara+ data)
                            (when viimeinen-rivi-yhteenveto?
                              [viimeinen-rivi])))
               data)]
    [:fo:table-body
     (when (empty? data)
       [:fo:table-row
        [:fo:table-cell {:padding "1mm"
                         :font-weight "normal"
                         :number-columns-spanned (count sarakkeet)}
         [:fo:block {:space-after "0.5em"}]
         [:fo:block (or tyhja "Ei tietoja")]]])
     (taulukko-rivit sarakkeet data viimeinen-rivi optiot)
     (taulukko-alaosa rivien-maara sarakkeet viimeinen-rivi-yhteenveto?)]))

(defmethod muodosta-pdf :taulukko [[_ {:keys [otsikko] :as optiot} sarakkeet data]]
  (let [sarakkeet (skeema/laske-sarakkeiden-leveys (keep identity sarakkeet))]
    [:fo:block {:space-before "1em" :font-size (str taulukon-fonttikoko taulukon-fonttikoko-yksikko) :font-weight "bold"} otsikko
     [:fo:table
      (for [{:keys [leveys]} sarakkeet]
        [:fo:table-column {:column-width leveys}])
      (taulukko-header optiot sarakkeet)
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
    (vis/bars {:width 180
               :height 80
               ;; tarvitaanko erityyppisille rapsoille eri formatteri?
               :format-amount (or fmt str)
               :hide-value? piilota-arvo?
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
  (binding [*orientaatio* (or (:orientaatio raportin-tunnistetiedot) :portrait)]
    (apply fo/dokumentti {:orientation *orientaatio*
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
                   #_[[:fo:block {:id "raportti-loppu"}]]))))

(def aikajana-rivimaara 25)

(defmethod muodosta-pdf :aikajana [[_ optiot rivit]]
  (let [rivit (map (fn [rivi]
                     (assoc rivi ::aikajana/ajat
                                 (map (fn [aika]
                                        (assoc aika
                                          ::aikajana/alku
                                          (when-let [alku (::aikajana/alku aika)]
                                            (pvm/suomen-aikavyohykkeeseen (c/from-sql-date alku)))
                                          ::aikajana/loppu
                                          (when-let [loppu (::aikajana/loppu aika)]
                                            (pvm/suomen-aikavyohykkeeseen (c/from-sql-date loppu)))))
                                      (::aikajana/ajat rivi))))
                   rivit)

        orientaatio (or *orientaatio* :landscape) ;; Dynamic binding ei toimi jos aikajana upotetaan toiseen raporttiin
        partitiot (partition aikajana-rivimaara aikajana-rivimaara nil rivit)
        aikajanat (map (fn [rows]
                         (aikajana/aikajana (merge {:leveys (case orientaatio
                                                              :portrait 750
                                                              :landscape 1000)}
                                                   optiot)
                                            rows))
                       partitiot)]
    [:fo:block
     (when (not-empty aikajanat)
       (for [aikajana aikajanat]
         [:fo:block
          [:fo:instream-foreign-object {:content-width (case orientaatio
                                                         :portrait "19cm"
                                                         :landscape "27.5cm")

                                        :content-height (str (+ 5 (count rivit)) "cm")}
           aikajana]]))]))

(defmethod muodosta-pdf :default [elementti]
  (log/debug "PDF-raportti ei tue elementtiä " elementti)
  nil)
