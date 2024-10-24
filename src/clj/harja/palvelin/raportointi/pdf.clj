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
            [harja.palvelin.raportointi.raportit.yleinen :as raportit-yleinen]
            [harja.ui.aikajana :as aikajana]
            [harja.pvm :as pvm]
            [clj-time.coerce :as c]))

(def ^:dynamic *orientaatio* nil)

(def taulukon-fonttikoko 8)
(def taulukon-fonttikoko-yksikko "pt")
(def otsikon-fonttikoko "10pt")
(def tekstin-fonttikoko "9pt")

(def raportin-tehostevari "#f0f0f0")
(def korostettu-vari "#004D99")
(def hennosti-korostettu-vari "#E0EDF9")
(def varoitus-vari "#f8d7d1")
(def huomio-vari "#FFF0BF")
(def harmaa-korostettu-vari "#FAFAFA")
(def harmaa-himmennys-vari "#858585")
(def valiotsikko-tumma-vari "#e1e1e1")
(def yhteenveto-tumma-vari "#fafafa")
(def varoitus-punainen-vari "#dd0000")

(defn- lisaa-non-breaking-spacet [val]
  (if (string? val)
    ;; Korvaa normaalit spacet non-breaking spaceilla
    (str/replace val #" " "\u00A0")
    val))

(defn- formatoija-fmt-mukaan [fmt]
  (case fmt
    ;; Jos halutaan tukea erityyppisiä sarakkeita,
    ;; pitää tänne lisätä formatter.
    :kokonaisluku #(raportti-domain/yrita (comp lisaa-non-breaking-spacet fmt/kokonaisluku-opt) %)
    :numero #(raportti-domain/yrita (comp lisaa-non-breaking-spacet fmt/desimaaliluku-opt) % 1 true)
    :numero-3desim #(raportti-domain/yrita (comp lisaa-non-breaking-spacet fmt/pyorista-ehka-kolmeen) %)
    :prosentti-0desim #(raportti-domain/yrita fmt/prosentti-opt % 0)
    :prosentti #(raportti-domain/yrita fmt/prosentti-opt %)
    :raha #(raportti-domain/yrita fmt/euro-opt %)
    :pvm #(raportti-domain/yrita fmt/pvm-opt %)
    str))

(defmulti muodosta-pdf
          "Muodostaa PDF:n XSL-FO hiccupin annetulle raporttielementille.
          Dispatch tyypin mukaan (vektorin 1. elementti)."
          (fn [elementti]
            (if (raportti-domain/raporttielementti? elementti)
              (first elementti)
              :vain-arvo)))

(def ^:const +max-rivimaara-default+ 1000)

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

(defmethod muodosta-pdf :arvo [[_ {:keys [arvo desimaalien-maara fmt ryhmitelty? jos-tyhja] :as elementti}]]
  (let [;; Negaativisille numeroille laitetaan negaatio, muuten ei tehdä mitään
        etuliite (if (and (number? arvo) (neg? arvo)) "-\u00A0" "")
        ;; Ja, koska etuliite lisätään käsin, pitää negaatio poistaa
        arvo (if (and (number? arvo) (neg? arvo)) (* -1 arvo)  arvo)]
    [:fo:inline
     [:fo:inline (if-not (nil? arvo)
                   (str etuliite
                     (cond
                       desimaalien-maara (fmt/desimaaliluku-opt arvo desimaalien-maara ryhmitelty?)
                       fmt (fmt arvo)
                       :else arvo))
                   jos-tyhja)]]))

(defmethod muodosta-pdf :liitteet [liitteet]
  (count (second liitteet)))

(defmethod muodosta-pdf :arvo-ja-osuus [[_ {:keys [arvo osuus fmt]}]]
  [:fo:inline
   [:fo:inline (if fmt (fmt arvo) arvo)]
   [:fo:inline " "]
   [:fo:inline {:font-size (str (- taulukon-fonttikoko 2) taulukon-fonttikoko-yksikko)} (str "( " osuus "%)")]])

;; Toimii tismalleen samoin, kuin :arvo-ja-yksikko, mutta tämän avulla
;; PDF:lle saadaan yksittäisille soluille korostuksia
(defmethod muodosta-pdf :arvo-ja-yksikko-korostettu [[_ {:keys [arvo yksikko fmt desimaalien-maara ryhmitelty?]}]]
  [:fo:inline
   [:fo:inline (cond
                 desimaalien-maara (fmt/desimaaliluku-opt arvo desimaalien-maara ryhmitelty?)
                 fmt (fmt arvo)
                 :else arvo)]
   [:fo:inline (str "\u00A0" yksikko)]])

(defmethod muodosta-pdf :arvo-ja-yksikko [[_ {:keys [arvo yksikko fmt desimaalien-maara ryhmitelty?]}]]
  [:fo:inline
   [:fo:inline (cond
                 desimaalien-maara (fmt/desimaaliluku-opt arvo desimaalien-maara ryhmitelty?)
                 fmt (fmt arvo)
                 :else arvo)]
   [:fo:inline (str "\u00A0" yksikko)]])

(defmethod muodosta-pdf :arvo-ja-selite [[_ {:keys [arvo selite]}]]
  [:fo:inline
   [:fo:inline (str arvo (when selite (str " (" selite ")")))]])

(defmethod muodosta-pdf :varillinen-teksti [[_ {:keys [arvo tyyli itsepaisesti-maaritelty-oma-vari fmt lihavoi? font-size himmenna?]}]]
  (let [tyyli {:color (or itsepaisesti-maaritelty-oma-vari
                          (raportti-domain/virhetyylit tyyli)
                          "black")}
        tyyli (if font-size (assoc tyyli :font-size font-size) tyyli)
        tyyli (if himmenna? (assoc tyyli :color harmaa-himmennys-vari) tyyli)
        tyyli (if lihavoi?
                (merge tyyli {:font-weight "bold"})
                tyyli)]
    ;; Muutettu inline -> block
    ;; Korjaa bugin päiväkirjaraportissa, ei vaikuta mitenkän ulkonäköön
    [:fo:block
     [:fo:inline tyyli
      (if fmt (fmt arvo) arvo)]]))

(defmethod muodosta-pdf :teksti-ja-info [[_ {:keys [arvo]}]] arvo)

(defmethod muodosta-pdf :infopallura [_]
  nil)

(defmethod muodosta-pdf :erotus-ja-prosentti [[_ {:keys [arvo prosentti desimaalien-maara  ryhmitelty?]}]]
  (let [etuliite (cond
                   (neg? arvo) "-\u00A0"  
                   (zero? arvo) ""
                   :else "+\u00A0")
        arvo (Math/abs (float arvo))
        prosentti (Math/abs (float prosentti))]
    [:fo:inline
     [:fo:inline (str etuliite
                   (cond
                     desimaalien-maara (-> (fmt/desimaaliluku-opt arvo desimaalien-maara ryhmitelty?)
                                         (lisaa-non-breaking-spacet))
                     :else arvo))]
     [:fo:inline (str "\n" "(" etuliite (fmt/prosentti-opt prosentti) ")")]]))

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
                    :background-color valiotsikko-tumma-vari
                    :number-columns-spanned (count sarakkeet)}
    [:fo:block {:space-after "0.5em"}]
    [:fo:block (cdata otsikko)]]])

(defn- korosta-kolumni-arvosta
  "Yleisesti PDF:n solun formatointi asetetaan rivitasolla. Tällä funktiolla voidaan määrittää
  solutasoisia korostuksia, eli eri värisiä taustoja.
  Käytetään soluelementille eli arvolle annettua korostusa, joita on kolme:
  ':korosta-hennosti?'
  ':varoitus?'
  ':huomio?'

  'arvo-datassa' on koko soluelementin sisältö ja jos sille on määritelty korostus, niin asettaan taustaväri korostuksen mukaan."
  [arvo-datassa]
  (let [korostusavain (if (and arvo-datassa (vector? arvo-datassa))
                        (cond
                          (:korosta-hennosti? (second arvo-datassa)) :korosta-hennosti?
                          (:varoitus? (second arvo-datassa)) :varoitus?
                          (:huomio? (second arvo-datassa)) :huomio?
                          :default :ei-korostusta)
                        :ei-korostusta)
        korostus-arvo-datassa (when (and
                                      arvo-datassa
                                      (vector? arvo-datassa)
                                      (korostusavain (second arvo-datassa)))
                                (korostusavain (second arvo-datassa)))
        taustavari (case korostusavain
                     :korosta-hennosti? hennosti-korostettu-vari
                     :varoitus? varoitus-vari
                     :huomio? huomio-vari
                     nil)
        korostus (cond
                   ;; korostusta ei ole asetettu data -elementtiin
                   (and
                     (raportti-domain/raporttielementti? arvo-datassa)
                     (false? korostus-arvo-datassa))
                   {}
                   (= korostusavain :ei-korostusta) {}
                   ;; Korostus asetettu data elementtiin
                   (and
                     (raportti-domain/raporttielementti? arvo-datassa)
                     korostus-arvo-datassa)
                   {:background-color taustavari
                    :color "black"}
                   :else {})]
    korostus))

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
                valkoinen? (:valkoinen? optiot)
                korosta-harmaa? (:korosta-harmaa? optiot)
                korosta-hennosti? (:korosta-hennosti? optiot)
                varoitus? (:varoitus? optiot)
                huomio? (:huomio? optiot)]]
      (if-let [otsikko (:otsikko optiot)]
        (taulukko-valiotsikko otsikko sarakkeet)
        (let [yhteenveto? (when (and viimeinen-rivi-yhteenveto?
                                     (= viimeinen-rivi rivi))
                            {:background-color yhteenveto-tumma-vari
                             :border (str "solid 0.3mm " raportin-tehostevari)
                             :font-weight "bold"})
              korosta? (when (or korosta-rivi? (some #(= i-rivi %) korosta-rivit))
                         {:background-color korostettu-vari
                          :color "white"})
              valkoinen? (when valkoinen?
                           {:background-color "white"
                            :color "black"})
              korosta-harmaa? (when korosta-harmaa?
                                  {:background-color harmaa-korostettu-vari
                                   :color "black"})
              korosta-hennosti? (when korosta-hennosti?
                                  {:background-color hennosti-korostettu-vari
                                   :color "black"})
              varoitus? (when varoitus?
                          {:background-color varoitus-vari
                           :color "black"})
              huomio? (when huomio?
                          {:background-color huomio-vari
                           :color "black"})
              lihavoi? (when lihavoi-rivi?
                         {:font-weight "bold"})]
          [:fo:table-row
           (for [i (range (count sarakkeet))
                 :let [arvo-datassa (nth rivi i nil)
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
                                :text-align (if (or (oikealle-tasattavat-kentat i)
                                                    (raportti-domain/numero-fmt? (:fmt sarake)))
                                              "right"
                                              (tasaus (:tasaa sarake)))}
                               yhteenveto?
                               korosta?
                               valkoinen?
                               korosta-harmaa?
                               ;; Rivin korostustiedot ajaa koluminikohtaisten korostusten yli.
                               ;; Tarkistetaan siis, onko jo korostukset olemassa, jos ei ole, niin haetaan arvo datasta eli kolumilta
                               (if (or korosta-hennosti? varoitus? huomio?)
                                 (first (filter #(not (nil? %)) (into #{} [korosta-hennosti? varoitus? huomio?])))
                                 (korosta-kolumni-arvosta arvo-datassa))
                               lihavoi?)
              (when korosta?
                [:fo:block {:space-after "0.2em"}])
              [:fo:block (if (string? naytettava-arvo)
                           (cdata (str naytettava-arvo))
                           naytettava-arvo)]])])))))

(defn- taulukko-alaosa [rivien-maara sarakkeet viimeinen-rivi-yhteenveto? rivi-raja]
  (when (> rivien-maara rivi-raja)
    [:fo:table-row
     [:fo:table-cell {:padding "1mm"
                      :number-columns-spanned (count sarakkeet)}
      [:fo:block {:space-after "0.5em"}]
      [:fo:block (str "Taulukossa näytetään vain ensimmäiset " rivi-raja " riviä. "
                   "Tarkenna hakuehtoa tai käytä Excel-vientiä."
                   (when viimeinen-rivi-yhteenveto?
                     "Yhteenveto on laskettu kaikista riveistä"))]]]))

(defn taulukko-header [{:keys [oikealle-tasattavat-kentat] :as optiot} sarakkeet]
  (let [oikealle-tasattavat-kentat (or oikealle-tasattavat-kentat #{})]
    [:fo:table-header
     (when-let [rivi-ennen (:rivi-ennen optiot)]
       [:fo:table-row
        (for [{:keys [teksti sarakkeita tasaa tummenna-teksti?]} rivi-ennen]
          [:fo:table-cell {:border reunan-tyyli
                           :background-color raportin-tehostevari
                           :color "black"
                           :number-columns-spanned (or sarakkeita 1)
                           :text-align (tasaus tasaa)}
           [:fo:block 
            (when tummenna-teksti? {:background-color valiotsikko-tumma-vari}) teksti]])])

     [:fo:table-row
      (map-indexed
        (fn [i {:keys [otsikko fmt tasaa] :as rivi}]
          [:fo:table-cell {:border "solid 0.1mm black"
                           :background-color raportin-tehostevari
                           :color "black"
                           :text-align (if (or (oikealle-tasattavat-kentat i)
                                               (raportti-domain/numero-fmt? fmt))
                                         "right"
                                         (tasaus tasaa))
                           :font-weight "normal" :padding "1mm"}
           [:fo:block (cdata otsikko)]])
        sarakkeet)]]))

(defn taulukko-body [sarakkeet data {:keys [viimeinen-rivi-yhteenveto? tyhja rajoita-pdf-rivimaara] :as optiot}]
  (let [rivien-maara (count data)
        viimeinen-rivi (last data)
        rivi-raja (or rajoita-pdf-rivimaara +max-rivimaara-default+)
        data (if (> (count data) rivi-raja)
               (vec (concat (take rivi-raja data)
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
     (taulukko-alaosa rivien-maara sarakkeet viimeinen-rivi-yhteenveto? rivi-raja)]))

(defn- skaalattu-fontin-koko [sarakkeet]
  (let [sarakkeet-lkm (count sarakkeet)]
    (str (float (- 1 (min 0.5 (* sarakkeet-lkm 0.025)))) "em")))

(defn taulukko [otsikko sarakkeet data {{:keys [skaalaa-teksti?]} :pdf-optiot :as optiot}]
  (let [sarakkeet (skeema/laske-sarakkeiden-leveys (keep identity sarakkeet))]
    [:fo:block {:space-before "1em" :font-size otsikon-fonttikoko :font-weight "bold"} otsikko
     ;; Taulukon fonttikoko skaalataan parent block-elementin font-size arvon mukaan
     ;; Mitä enemmän sarakkeita, sitä pienempi fonttikoko. Lähtöarvona on parent block-elementin font-size.
     [:fo:table (when skaalaa-teksti?
                  {:font-size (skaalattu-fontin-koko sarakkeet)})
      (for [{:keys [leveys]} sarakkeet]
        [:fo:table-column {:column-width leveys}])
      (taulukko-header optiot sarakkeet)
      (taulukko-body sarakkeet data optiot)]
     [:fo:block {:space-after "1em"}]]))

(defmethod muodosta-pdf :taulukko [[_ {:keys [otsikko] :as optiot} sarakkeet data]]
  (taulukko otsikko sarakkeet data optiot))

(defmethod muodosta-pdf :liitteet [liitteet]
  (count (second liitteet)))

(defmethod muodosta-pdf :jakaja [[_ margin]]
  (let [tyyli {:border "solid 0.1mm gray"}
        ;; Jos haluaan ""poistaa margin"" jakajasta, laitetaan vaan 8px
        margin-px (if-not (= margin :poista-margin) "30px" "8px")
        tyyli (assoc tyyli
                :margin-top margin-px
                :margin-bottom margin-px)]
    [:fo:block tyyli]))

(defmethod muodosta-pdf :otsikko [[_ teksti]]
  [:fo:block {:padding-top "5mm"
              :font-size otsikon-fonttikoko
              :font-weight 600} teksti])

(defmethod muodosta-pdf :otsikko-heading [[_ teksti]]
  [:fo:block {:padding-top "5mm" :font-size "9pt"} teksti])

(defmethod muodosta-pdf :otsikko-heading-small [[_ teksti]]
  [:fo:block {:padding-top "5mm" :font-size "8pt"} teksti])

(defmethod muodosta-pdf :otsikko-kuin-pylvaissa [[_ teksti]]
  [:fo:block {:font-weight "bold"
              :margin-bottom "2mm"
              :margin-top "2mm"} teksti])


(defmethod muodosta-pdf :teksti [[_ teksti {:keys [vari]}]]
  [:fo:block {:color (when vari vari)
              :font-size otsikon-fonttikoko} teksti])

(defmethod muodosta-pdf :osittain-boldattu-teksti
  ;; Joihinkin teksteihin halutaan osittain boldattu teksti. Tämä elementti mahdollistaa sen.
  [[_ {:keys [boldattu-teksti teksti] :as tiedot}]]
  [:fo:block
   [:fo:inline {:font-size tekstin-fonttikoko
                :font-weight 600} boldattu-teksti]
   [:fo:inline {:font-size tekstin-fonttikoko
                :font-weight 100} teksti]])

(defmethod muodosta-pdf :teksti-paksu [[_ teksti {:keys [vari]}]]
  [:fo:block {:color (when vari vari)
              :font-size otsikon-fonttikoko
              :font-weight "bold"} teksti])

(defmethod muodosta-pdf :varoitusteksti [[_ teksti]]
  (muodosta-pdf [:teksti teksti {:vari varoitus-punainen-vari}]))

(defmethod muodosta-pdf :infolaatikko [[_ teksti {:keys [tyyppi toissijainen-viesti leveys rivita?]}]]
  ;; TODO: Infolaatikon renderöintiä ei toistaiseksi tueta. Toteutetaan, jos tarve ilmenee.
  nil)

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
      (let [arvo (if (and
                      (= arvo nil)
                      (= otsikko "Urakka")) "Kaikki" arvo)
            arvo (if (and
                      (= arvo nil)
                      (= otsikko "Urakoitsija")) "-" arvo)]
        [:fo:table-row
         [:fo:table-cell
          [:fo:block {:text-align "right" :font-weight "bold"}
           (let [otsikko (str/trim (str otsikko))]
             (if (.endsWith otsikko ":")
               otsikko
               (str otsikko ":")))]]
         [:fo:table-cell
          [:fo:block {:margin-left "5mm"} (str arvo)]]]))]])

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

(defmethod muodosta-pdf :checkbox-lista [[_ otsikot-ja-arvot]]
  [:fo:table {:font-size otsikon-fonttikoko}
   [:fo:table-body
    [:fo:table-row
     (for [[otsikko vaihtoehto koko] otsikot-ja-arvot]
       [:fo:table-cell
        [:fo:block
            (fo/checkbox koko vaihtoehto)
            " " otsikko]])]]])

(defmethod muodosta-pdf :raportti [[_ raportin-tunnistetiedot & sisalto]]
  ;; Muodosta header raportin-tunnistetiedoista!
  (let [tiedoston-nimi (raportit-yleinen/raportti-tiedostonimi raportin-tunnistetiedot)]
    (with-meta
      (binding [*orientaatio* (or (:orientaatio raportin-tunnistetiedot) :portrait)]
        (apply fo/dokumentti {:orientation *orientaatio*
                              :header {:sisalto (luo-header (:nimi raportin-tunnistetiedot))}}
          (concat [;; Jos raportin tunnistetiedoissa on annettu :tietoja avaimella, näytetään ne alussa
                   (when-let [tiedot (:tietoja raportin-tunnistetiedot)]
                     [:fo:block {:padding "1mm 0" :border "solid 0.2mm black" :margin-bottom "2mm"}
                      (muodosta-pdf [:yhteenveto tiedot])])]

            (keep identity
              (mapcat (fn [elem]
                        (when elem
                          (if (seq? elem)
                            ;; Passaa taulukolle pdf rajoitus 
                            (map #(if (= (first %) :taulukko)
                                    (muodosta-pdf (update % 1 assoc :rajoita-pdf-rivimaara (:rajoita-pdf-rivimaara raportin-tunnistetiedot)))
                                    (muodosta-pdf %))
                              elem)
                            ;; Passaa taulukolle pdf rajoitus 
                            (if (= (first elem) :taulukko)
                              [(muodosta-pdf (update elem 1 assoc :rajoita-pdf-rivimaara (:rajoita-pdf-rivimaara raportin-tunnistetiedot)))]
                              [(muodosta-pdf elem)]))))
                sisalto))

            #_[[:fo:block {:id "raportti-loppu"}]])))
      {:tiedostonimi (str tiedoston-nimi ".pdf")})))

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

(defmethod muodosta-pdf :boolean [[_ {:keys [arvo]}]]
  (if arvo "Kyllä" "Ei"))

(defmethod muodosta-pdf :default [elementti]
  (log/debug "PDF-raportti ei tue elementtiä " elementti)
  nil)
