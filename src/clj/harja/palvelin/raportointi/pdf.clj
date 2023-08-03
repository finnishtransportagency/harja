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

(def raportin-tehostevari "#f0f0f0")
(def korostettu-vari "#004D99")
(def hennosti-korostettu-vari "#E0EDF9")
(def harmaa-korostettu-vari "#FAFAFA")

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

(defmethod muodosta-pdf :arvo [[_ {:keys [arvo desimaalien-maara fmt ryhmitelty? jos-tyhja] :as elementti}]]
  [:fo:inline
   [:fo:inline (if-not (nil? arvo)
                 (cond
                   desimaalien-maara (fmt/desimaaliluku-opt arvo desimaalien-maara ryhmitelty?)
                   fmt (fmt arvo)
                   :else arvo)
                 jos-tyhja)]])

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
        tyyli (if himmenna? (assoc tyyli :color "#858585") tyyli)
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
                     desimaalien-maara (fmt/desimaaliluku-opt arvo desimaalien-maara  ryhmitelty?)
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
                    :background-color "#e1e1e1"
                    :number-columns-spanned (count sarakkeet)}
    [:fo:block {:space-after "0.5em"}]
    [:fo:block (cdata otsikko)]]])

(defn- formatoija-fmt-mukaan [fmt]
  (case fmt
    ;; Jos halutaan tukea erityyppisiä sarakkeita,
    ;; pitää tänne lisätä formatter.
    :kokonaisluku #(raportti-domain/yrita fmt/kokonaisluku-opt %)
    :numero #(raportti-domain/yrita fmt/desimaaliluku-opt % 1 true)
    :numero-3desim #(fmt/pyorista-ehka-kolmeen %)
    :prosentti-0desim #(raportti-domain/yrita fmt/prosentti-opt % 0)
    :prosentti #(raportti-domain/yrita fmt/prosentti-opt %)
    :raha #(raportti-domain/yrita fmt/euro-opt %)
    :pvm #(raportti-domain/yrita fmt/pvm-opt %)
    str))

(defn- korostetaanko-hennosti
  "Yleisesti PDF:n solun formatointi asetetaan rivitasolla. Tällä funktiolla voidaan määrittää
  solutasoisia hentoja korostuksia, eli vaalean sinistä taustaa.
  Käytetään soluelementille annettua hento-korostus-arvoa ensisijaisesti. Toissijaisesti käytetään riville annetta.

  'korosta-hennosti?' ensimmäinen parametri tulee rivitasolta.
  'arvo-datassa' on koko soluelementin sisältö ja jos sille on määritelty hento korostus, niin asettaan taustaväri."
  [korosta-hennosti? arvo-datassa]
  (cond
    (and
      (raportti-domain/raporttielementti? arvo-datassa)
      (false? (:korosta-hennosti? (second arvo-datassa))))
    {}
    korosta-hennosti? korosta-hennosti?
    (and
      (raportti-domain/raporttielementti? arvo-datassa)
      (:korosta-hennosti? (second arvo-datassa)))
    {:background-color hennosti-korostettu-vari
     :color "black"}
    :else {}))

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
                korosta-hennosti? (:korosta-hennosti? optiot)]]
      (if-let [otsikko (:otsikko optiot)]
        (taulukko-valiotsikko otsikko sarakkeet)
        (let [yhteenveto? (when (and viimeinen-rivi-yhteenveto?
                                     (= viimeinen-rivi rivi))
                            {:background-color "#fafafa"
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
                                :text-align (if (or (oikealle-tasattavat-kentat i)
                                                    (raportti-domain/numero-fmt? (:fmt sarake)))
                                              "right"
                                              (tasaus (:tasaa sarake)))}
                               yhteenveto?
                               korosta?
                               valkoinen?
                               korosta-harmaa?
                               (korostetaanko-hennosti korosta-hennosti? arvo-datassa)
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

(defn taulukko-header [{:keys [oikealle-tasattavat-kentat] :as optiot} sarakkeet]
  (let [oikealle-tasattavat-kentat (or oikealle-tasattavat-kentat #{})]
    [:fo:table-header
     (when-let [rivi-ennen (:rivi-ennen optiot)]
       [:fo:table-row
        (for [{:keys [teksti sarakkeita tasaa]} rivi-ennen]
          [:fo:table-cell {:border reunan-tyyli
                           :background-color raportin-tehostevari
                           :color "black"
                           :number-columns-spanned (or sarakkeita 1)
                           :text-align (tasaus tasaa)}
           [:fo:block teksti]])])

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

(defn arvotaulukko-valittu-aika [kyseessa-kk-vali? otsikko hoitokauden-otsikko valittu-pvm-otsikko hoitokauden-arvo laskutetaan-arvo]

  [:fo:table {:font-size "9pt" :margin-bottom "12px"}
   [:fo:table-column {:column-width "56%"}]

   [:fo:table-column {:column-width "20%"}]
   [:fo:table-column {:column-width "20%"}]

   [:fo:table-body
    [:fo:table-row
     ;; Selitys
     [:fo:table-cell [:fo:block {:font-weight "bold"} otsikko]]
     ;; "Hoitokauden alusta" & "Laskutetaan 0x/0x"
     [:fo:table-cell [:fo:block {:font-weight "bold"} hoitokauden-otsikko]]
     (when kyseessa-kk-vali?
       [:fo:table-cell [:fo:block {:font-weight "bold"} valittu-pvm-otsikko]])]

    ;; Arvot rahana
    [:fo:table-row
     [:fo:table-cell [:fo:block ""]]
     [:fo:table-cell [:fo:block hoitokauden-arvo]]
     (when kyseessa-kk-vali?
       [:fo:table-cell [:fo:block laskutetaan-arvo]])]]])

(defn arvotaulukko-ei-valittua-aikaa [otsikko hoitokauden-arvo]
  [:fo:table {:font-size "9pt"}
   [:fo:table-column {:column-width "56%"}]
   [:fo:table-column {:column-width "20%"}]

   [:fo:table-body
    [:fo:table-row
     [:fo:table-cell [:fo:block {:font-weight "bold"} otsikko]]
     [:fo:table-cell [:fo:block hoitokauden-arvo]]]]])

(defn hoitokausi-kuukausi-arvotaulukko [sarakkeet tiedot]
  ;; Käytetään hoitokauden & valitun kuukauden raha-arvojen näyttöön 
  ;; Sarakkeet pitää sisältää hoitokauden & valitun kuukauden otsikot, esim: (Hoitokauden alusta, Laskutetaan 09/20)
  ;; Tiedoissa on raha-arvot desimaaleina (BigDecimal) ja niiden selitykset (str), esim: (Muut kustannukset yhteensä, 700.369M 0.0M)
  ;; Mikäli selityksen jälkeen on 2 desimaalia, funktio generoi <Hoitokausi> & <valittu kk> -otsikot joiden alla näkyy arvot
  ;; Mikäli selityksen jälkeen on vain 1 desimaali, ei erillisiä otsikkoja tehdä, vaan näytetään pelkästään <selitys: > <arvo>

  ;; Esimerkki 1: 
  ;; OTSIKOT:  ()
  ;; ARVOT:  (Hankinnat ja hoidonjohto yhteensä 123.123M)
  ;; Näytetään seuraavasti: "Hankinnat ja hoidonjohto yhteensä: 123.123 €"

  ;; Esimerkki 2: 
  ;; OTSIKOT: (Hoitokauden alusta Laskutetaan 09/20)
  ;; ARVOT: (Toteutuneet kustannukset 123.123M 0.0M)
  ;; Näytetään seuraavasti: "Toteutuneet kustannukset:  Hoitokauden alusta   Laskutetaan 09/20
  ;;                                                       123.123 €              0.0 €       "

  (let [laskutus-otsikot (raportti-domain/hoitokausi-kuukausi-laskutus-otsikot sarakkeet) ;; "Hoitokauden alusta" & "Laskutetaan 0x/0x"
        hoitokauden-otsikko (first laskutus-otsikot)
        valittu-pvm-otsikko (second laskutus-otsikot)

        ;; Hakee taulukon arvot, selitys on string jonka perässä laskutus arvot raha desimaaleina
        arvot (raportti-domain/hoitokausi-kuukausi-arvot tiedot decimal?)
        koko (dec (count arvot))]

    ;; Haetaan selitysten arvot
    ;; Jos arvo sisältää 2 desimaali-muuttujaa, tälle tulee hoitokausi/laskutetaan otsikot
    (for [[n elem] (map-indexed #(vector %1 %2) arvot)]

      ;; Alkaa aina otsikolla joka on string
      (when (string? elem)
        (if (>= koko (+ n 2))
          (let [hoitokauden-arvo (nth arvot (inc n))
                laskutetaan-arvo (nth arvot (+ n 2))]

            (if (decimal? laskutetaan-arvo)

              ;; Jos otsikolla on 2 desimaali-muuttujaa, tehdään 2 otsikkoa lisää ja annetaan niiden alle arvot
              (arvotaulukko-valittu-aika
               true
               (str elem ":")
               (str hoitokauden-otsikko)
               (str valittu-pvm-otsikko)
               (str (fmt/euro hoitokauden-arvo))
               (str (fmt/euro laskutetaan-arvo)))

              ;; Seuraava muuttuja on string, eli otsikko ja arvo
              (arvotaulukko-ei-valittua-aikaa
               (str elem ":")
               (str (fmt/euro hoitokauden-arvo)))))

          ;; Muuttujia ei ole kun 2, eli otsikko ja arvo
          (let [hoitokauden-arvo (nth arvot (inc n))]
            (arvotaulukko-ei-valittua-aikaa
             (str elem ":")
             (str (fmt/euro hoitokauden-arvo)))))))))

(defn taulukko [otsikko hoitokausi-arvotaulukko? sarakkeet data optiot]
  (let [sarakkeet (skeema/laske-sarakkeiden-leveys (keep identity sarakkeet))]
    (if hoitokausi-arvotaulukko?
      (hoitokausi-kuukausi-arvotaulukko sarakkeet data)

      [:fo:block {:space-before "1em" :font-size (str taulukon-fonttikoko taulukon-fonttikoko-yksikko) :font-weight "bold"} otsikko
       [:fo:table
        (for [{:keys [leveys]} sarakkeet]
          [:fo:table-column {:column-width leveys}])
        (taulukko-header optiot sarakkeet)
        (taulukko-body sarakkeet data optiot)]
       [:fo:block {:space-after "1em"}]])))

(defmethod muodosta-pdf :taulukko [[_ {:keys [otsikko hoitokausi-arvotaulukko?] :as optiot} sarakkeet data]]
  (taulukko otsikko hoitokausi-arvotaulukko? sarakkeet data optiot))

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
  [:fo:block {:padding-top "5mm" :font-size otsikon-fonttikoko} teksti])

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

(defmethod muodosta-pdf :teksti-paksu [[_ teksti {:keys [vari]}]]
  [:fo:block {:color (when vari vari)
              :font-size otsikon-fonttikoko
              :font-weight "bold"} teksti])

(defmethod muodosta-pdf :varoitusteksti [[_ teksti]]
  (muodosta-pdf [:teksti teksti {:vari "#dd0000"}]))

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
              (mapcat #(when %
                         (if (seq? %)
                           (map muodosta-pdf %)
                           [(muodosta-pdf %)]))
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
