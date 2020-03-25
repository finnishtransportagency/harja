(ns harja.ui.kentat
  "UI-input kenttien muodostaminen tyypin perusteella, esim. grid ja lomake komponentteihin."
  (:require [reagent.core :refer [atom] :as r]
            [harja.pvm :as pvm]
            [harja.ui.pvm :as pvm-valinta]
            [harja.ui.protokollat :refer [hae]]
            [harja.ui.komponentti :as komp]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.tierekisteri :as tr]
            [harja.ui.sijaintivalitsin :as sijaintivalitsin]
            [harja.ui.yleiset :refer [linkki ajax-loader livi-pudotusvalikko nuolivalinta
                                      maarita-pudotusvalikon-suunta-ja-max-korkeus avautumissuunta-ja-korkeus-tyylit]]
            [harja.ui.napit :as napit]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.sijaintivalitsin :as sijaintivalitsin-tiedot]
            [clojure.string :as str]
            [clojure.set :as s]
            [goog.string :as gstr]
            [goog.events.EventType :as EventType]
            [cljs.core.async :refer [<! >! chan] :as async]

            [harja.ui.dom :as dom]
            [harja.ui.kartta.ikonit :as kartta-ikonit]
            [harja.tiedot.kartta :as kartta]
            [harja.ui.kartta.esitettavat-asiat :refer [maarittele-feature]]
            [harja.views.kartta.tasot :as tasot]
            [harja.geo :as geo]

    ;; Tierekisteriosoitteen muuntaminen sijainniksi tarvii tämän
            [harja.tyokalut.vkm :as vkm]
            [harja.atom :refer [paivittaja]]
            [harja.fmt :as fmt]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.kartta.varit.puhtaat :as puhtaat]
            [harja.ui.kartta.asioiden-ulkoasu :as asioiden-ulkoasu]
            [harja.ui.yleiset :as y]
            [harja.domain.tierekisteri :as trd]
            [harja.views.kartta.tasot :as karttatasot]
            [harja.tyokalut.big :as big]
            [taoensso.timbre :as log])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [harja.tyokalut.ui :refer [for*]]
                   [harja.makrot :refer [nappaa-virhe]]))

;; PENDING: dokumentoi rajapinta, mitä eri avaimia kentälle voi antaa


;; r/wrap skeeman arvolle
(defn atomina [{:keys [nimi hae aseta]} data vaihda!]
  (let [hae (or hae #(get % nimi))
        arvo (hae data)]
    (r/wrap arvo
            (fn [uusi]
              ;; Resetoi data, jos uusi data annettu
              (when (not= uusi arvo)
                (if aseta
                  (vaihda! (aseta data uusi))
                  (vaihda! (assoc data nimi uusi))))))))

(defn vain-luku-atomina [arvo]
  (r/wrap arvo
          #(assert false (str "Ei voi kirjoittaa vain luku atomia arvolle: " (pr-str arvo)))))

(defmulti tee-kentta
          "Tekee muokattavan kentän tyypin perusteella"
          (fn [t & args] (:tyyppi t)))

(defmulti nayta-arvo
          "Tekee vain-luku näyttömuodon kentän arvosta tyypin perusteella.
          Tämän tarkoituksena ei ole tuottaa 'disabled' tai 'read-only' elementtejä
          vaan tekstimuotoinen kuvaus arvosta. Oletustoteutus muuntaa datan vain merkkijonoksi."
          (fn [t & args] (:tyyppi t)))

(defmethod nayta-arvo :default [_ data]
  [:span (str @data)])

(defmethod nayta-arvo :komponentti [skeema data]
  (let [komponentti (:komponentti skeema)]
    [komponentti data]))

(defmethod tee-kentta :haku [{:keys [lahde nayta placeholder pituus lomake? sort-fn disabled?
                                     kun-muuttuu hae-kun-yli-n-merkkia]} data]
  (let [nyt-valittu @data
        teksti (atom (if nyt-valittu
                       ((or nayta str) nyt-valittu) ""))
        tulokset (atom nil)
        valittu-idx (atom nil)
        hae-kun-yli-n-merkkia (or hae-kun-yli-n-merkkia 2)
        avautumissuunta (atom :alas)
        max-korkeus (atom 0)
        pudotusvalikon-korkeuden-kasittelija-fn (fn [this _]
                                                  (let [maaritys (maarita-pudotusvalikon-suunta-ja-max-korkeus this)]
                                                    (reset! avautumissuunta (:suunta maaritys))
                                                    (reset! max-korkeus (:max-korkeus maaritys))))]
    (komp/luo
      (komp/dom-kuuntelija js/window
                           EventType/SCROLL pudotusvalikon-korkeuden-kasittelija-fn
                           EventType/RESIZE pudotusvalikon-korkeuden-kasittelija-fn)
      (komp/klikattu-ulkopuolelle #(reset! tulokset nil))
      {:component-did-mount
       (fn [this]
         (pudotusvalikon-korkeuden-kasittelija-fn this nil))}

      (fn [_ data]
        [:div.hakukentta.dropdown {:class (when (some? @tulokset) "open")}

         [:input {:class (cond-> nil
                                 lomake? (str "form-control ")
                                 disabled? (str "disabled"))
                  :value @teksti
                  :placeholder placeholder
                  :disabled disabled?
                  :size pituus
                  :on-change #(when (= (.-activeElement js/document) (.-target %))
                                ;; tehdään haku vain jos elementti on fokusoitu
                                ;; IE triggeröi on-change myös ohjelmallisista muutoksista
                                (let [v (-> % .-target .-value str/triml)]
                                  (reset! data nil)
                                  (reset! teksti v)
                                  (when kun-muuttuu (kun-muuttuu v))
                                  (if (> (count v) hae-kun-yli-n-merkkia)
                                    (do (reset! tulokset :haetaan)
                                        (go (let [tul (<! (hae lahde v))]
                                              (reset! tulokset tul)
                                              (reset! valittu-idx nil))))
                                    (reset! tulokset nil))))
                  :on-key-down (nuolivalinta #(let [t @tulokset]
                                                (log "YLÖS " @valittu-idx)
                                                (when (vector? t)
                                                  (swap! valittu-idx
                                                         (fn [idx]
                                                           (if (or (= 0 idx) (nil? idx))
                                                             (dec (count t))
                                                             (dec idx))))))
                                             #(let [t @tulokset]
                                                (log "ALAS " @valittu-idx)
                                                (when (vector? t)
                                                  (swap! valittu-idx
                                                         (fn [idx]
                                                           (if (and (nil? idx) (not (empty? t)))
                                                             0
                                                             (if (< idx (dec (count t)))
                                                               (inc idx)
                                                               0))))))
                                             #(let [t @tulokset
                                                    idx @valittu-idx]
                                                (when (number? idx)
                                                  (let [v (nth t idx)]
                                                    (reset! data v)
                                                    (reset! teksti ((or nayta str) v))
                                                    (when kun-muuttuu (kun-muuttuu nil))
                                                    (reset! tulokset nil)))))}]
         (when (zero? hae-kun-yli-n-merkkia)
           [:button.nappi-hakualasveto
            {:on-click #(go (reset! tulokset (<! (hae lahde "")))
                            (reset! valittu-idx nil))
             :disabled disabled?}
            [:span.livicon-chevron-down]])

         [:ul.hakukentan-lista.dropdown-menu {:role "menu"
                                              :style (avautumissuunta-ja-korkeus-tyylit
                                                       @max-korkeus @avautumissuunta)}
          (let [nykyiset-tulokset (if (and sort-fn (vector? @tulokset))
                                    (sort-by sort-fn @tulokset)
                                    @tulokset)
                idx @valittu-idx]
            (if (= :haetaan nykyiset-tulokset)
              [:li {:role "presentation"} (ajax-loader) " haetaan: " @teksti]
              (if (empty? nykyiset-tulokset)
                [:span.ei-hakutuloksia "Ei tuloksia"]
                (doall (map-indexed (fn [i t]
                                      ^{:key (hash t)}
                                      [:li {:class (when (= i idx) "korostettu") :role "presentation"}
                                       [linkki ((or nayta str) t) #(do
                                                                     (reset! data t)
                                                                     (reset! teksti ((or nayta str) t))
                                                                     (when kun-muuttuu (kun-muuttuu nil))
                                                                     (reset! tulokset nil))]])
                                    nykyiset-tulokset)))))]]))))


(defn placeholder [{:keys [placeholder placeholder-fn rivi] :as kentta} data]
  (or placeholder
      (and placeholder-fn (placeholder-fn rivi))))

(defmethod tee-kentta :string [{:keys [nimi pituus-max pituus-min regex focus on-focus on-blur lomake? toiminta-f disabled?]
                                :as kentta} data]
  [:input {:class (cond-> nil
                          lomake? (str "form-control ")
                          disabled? (str "disabled"))
           :placeholder (placeholder kentta data)
           :on-change #(let [v (-> % .-target .-value)]
                         (when (or (not regex) (re-matches regex v))
                           (reset! data v)
                           (when toiminta-f
                             (toiminta-f v))))
           :disabled disabled?
           :on-focus on-focus
           :on-blur on-blur
           :value @data
           :max-length pituus-max}])

(defmethod tee-kentta :linkki [opts data]
  [tee-kentta (assoc opts :tyyppi :string) data])

(defmethod nayta-arvo :linkki [_ data]
  [:a {:href @data} @data])


;; Pitkä tekstikenttä käytettäväksi lomakkeissa, ei sovellu hyvin gridiin
;; pituus-max oletusarvo on 256, koska se on toteuman lisätiedon tietokantasarakkeissa
(defmethod tee-kentta :text [{:keys [placeholder nimi koko on-focus on-blur lomake? pituus-max toiminta-f]} data]
  (let [[koko-sarakkeet koko-rivit] koko
        rivit (atom (if (= :auto koko-rivit)
                      1
                      koko-rivit))
        pituus-max (or pituus-max 256)
        muuta! (fn [data e]
                 ;; alla pientä workaroundia koska selaimen max-length -ominaisuus ei tue rivinvaihtoja
                 (let [teksti (-> e .-target .-value)]
                   (when-not
                     ;; IE11 laukaisee oudon change eventin initial renderissä
                     ;; joka johtaa kentän validoimiseen, estetään käsittely
                     ;; jos teksti on tyhjä ja data on nil
                     (and (empty? teksti)
                          (nil? @data))
                     ;; jos copy-paste ylittäisi max-pituuden, eipä sallita sitä
                     (let [teksti (if (< (count teksti) pituus-max)
                                    teksti
                                    (subs teksti 0 pituus-max))]
                       (reset! data teksti)
                       (when toiminta-f
                         (toiminta-f teksti))))))]
    (komp/luo
      (when (= koko-rivit :auto)
        {:component-did-update
         (fn [this _]
           (let [n (-> this r/dom-node
                       (.getElementsByTagName "textarea")
                       (aget 0))
                 erotus (- (.-scrollHeight n) (.-clientHeight n))]
             (when (> erotus 1)                             ;; IE11 näyttää aluksi 24 vs 25
               (swap! rivit + (/ erotus 19)))))})

      (fn [{:keys [nimi koko on-focus on-blur lomake? disabled?]} data]
        [:span.kentta-text
         [:textarea {:value @data
                     :on-change #(muuta! data %)
                     :on-focus on-focus
                     :on-blur on-blur
                     :disabled disabled?
                     :cols (or koko-sarakkeet 80)
                     :rows @rivit
                     :class (cond-> nil
                                    lomake? (str "form-control ")
                                    disabled? (str "disabled"))
                     :placeholder placeholder}]
         ;; näytetään laskuri kun merkkejä on jäljellä alle 25%
         (when (> (/ (count @data) pituus-max) 0.75)
           [:div (- pituus-max (count @data)) " merkkiä jäljellä"])]))))

(defn- normalisoi-numero [str]
  (-> str
      ;; Poistetaan whitespace
      (str/replace #"\s" "")

      ;; Poistetaan mahd. euromerkki lopusta
      (str/replace #"€$" "")))

(def +desimaalin-oletus-tarkkuus+ 2)

(defmethod tee-kentta :numero [{:keys [oletusarvo] :as kentta} data]
  (let [fmt (or
              (when-let [tarkkuus (:desimaalien-maara kentta)]
                #(fmt/desimaaliluku-opt % tarkkuus))
              (:fmt kentta) str)
        teksti (atom nil)
        kokonaisosan-maara (or (:kokonaisosan-maara kentta) 10)]
    (komp/luo
      (komp/nimi "Numerokenttä")
      (komp/piirretty #(when (and oletusarvo (nil? @data)) (reset! data oletusarvo)))
      (fn [{:keys [lomake? kokonaisluku? vaadi-ei-negatiivinen? toiminta-f on-blur on-focus disabled?] :as kentta} data]
        (let [nykyinen-data @data
              nykyinen-teksti (or @teksti
                                  (normalisoi-numero (fmt nykyinen-data))
                                  "")
              kokonaisluku-re-pattern (re-pattern (str "-?\\d{1," kokonaisosan-maara "}"))
              desimaaliluku-re-pattern (re-pattern (str "-?\\d{1," kokonaisosan-maara "}((\\.|,)\\d{0,"
                                                        (or (:desimaalien-maara kentta) +desimaalin-oletus-tarkkuus+)
                                                        "})?"))]
          [:input {:class (cond-> nil
                                  lomake? (str "form-control ")
                                  disabled? (str "disabled"))
                   :type "text"
                   :disabled disabled?
                   :placeholder (placeholder kentta data)
                   :on-focus #(when on-focus (on-focus))
                   :on-blur #(do (when on-blur
                                   (on-blur %))
                                 (reset! teksti nil))
                   :value nykyinen-teksti
                   :on-change #(let [v (normalisoi-numero (-> % .-target .-value))
                                     v (if vaadi-ei-negatiivinen?
                                         (str/replace v #"-" "")
                                         v)]
                                 (when (or (= v "")
                                           (when-not vaadi-ei-negatiivinen? (= v "-"))
                                           ;; Halutaan että käyttäjä voi muokata desimaaliluvun esim ",0" muotoon,
                                           ;; mutta tätä välivaihetta ei tallenneta dataan
                                           (re-matches #"[0-9,.-]+" v))
                                   (reset! teksti v)

                                   (let [numero (if kokonaisluku?
                                                  (js/parseInt v)
                                                  (js/parseFloat (str/replace v #"," ".")))]
                                     (if (not (js/isNaN numero))
                                       (reset! data numero)
                                       (reset! data nil))
                                     (when toiminta-f
                                       (toiminta-f (when-not (js/isNaN numero)
                                                     numero))))))}])))))

(defmethod nayta-arvo :numero [{:keys [kokonaisluku? desimaalien-maara] :as kentta} data]
  (let [desimaalien-maara (or (when kokonaisluku? 0) desimaalien-maara +desimaalin-oletus-tarkkuus+)
        fmt #(fmt/desimaaliluku-opt % desimaalien-maara)]
    [:span (normalisoi-numero (fmt @data))]))

(defmethod tee-kentta :positiivinen-numero [kentta data]
  [tee-kentta (assoc kentta :vaadi-ei-negatiivinen? true
                     :tyyppi :numero) data])

(defmethod nayta-arvo :positiivinen-numero [kentta data]
  [nayta-arvo (assoc kentta :tyyppi :numero) data])


(defmethod tee-kentta :big [{:keys [lomake? desimaalien-maara placeholder]} data]
  (let [fmt #(big/fmt % desimaalien-maara)
        teksti (atom (some-> @data fmt))
        pattern (re-pattern (str "^(\\d+([.,]\\d{0," desimaalien-maara "})?)?$"))]
    (fn [{:keys [lomake? desimaalien-maara disabled?]} data]
      [:input {:class (cond-> nil
                              lomake? (str "form-control ")
                              disabled? (str "disabled"))
               :placeholder placeholder
               :disabled disabled?
               :type "text"
               :value @teksti
               :on-change #(let [txt (-> % .-target .-value)]
                             (when (re-matches pattern txt)
                               (reset! teksti txt)
                               (reset! data (big/parse txt))))
               :on-blur #(reset! teksti (some-> @data fmt))}])))

(defmethod nayta-arvo :big [{:keys [desimaalien-maara]} data]
  [:span (some-> @data (big/fmt desimaalien-maara))])

(defmethod tee-kentta :email [{:keys [on-focus on-blur lomake? disabled?] :as kentta} data]
  [:input {:class (cond-> nil
                          lomake? (str "form-control ")
                          disabled? (str "disabled"))
           :type "email"
           :value @data
           :disabled disabled?
           :on-focus on-focus
           :on-blur on-blur
           :on-change #(reset! data (-> % .-target .-value))}])



(defmethod tee-kentta :puhelin [{:keys [on-focus on-blur pituus lomake? placeholder disabled?] :as kentta} data]
  [:input {:class (cond-> nil
                          lomake? (str "form-control ")
                          disabled? (str "disabled"))
           :type "tel"
           :value @data
           :max-length pituus
           :disabled disabled?
           :on-focus on-focus
           :on-blur on-blur
           :placeholder placeholder
           :on-change #(let [uusi (-> % .-target .-value)]
                         (when (re-matches #"\+?(\s|\d)*" uusi)
                           (reset! data uusi)))}])


(defmethod tee-kentta :radio [{:keys [valinta-nayta valinta-arvo valinnat on-focus on-blur disabled?]} data]
  (let [arvo (or valinta-arvo identity)
        nayta (or valinta-nayta str)
        nykyinen-arvo @data]
    (if-let [valinta (and (= 1 (count valinnat))
                          (first valinnat))]
      (let [arvo (arvo valinta)
            valitse #(reset! data arvo)
            label (nayta valinta)]
        [:span {:style {:width "100%" :height "100%" :display "inline-block"}
                :on-click valitse}
         [:input {:type "radio"
                  :value 1
                  :disabled disabled?
                  :checked (= nykyinen-arvo arvo)
                  :on-change valitse}]
         (when-not (str/blank? label)
           [:span.radiovalinta-label.klikattava {:on-click valitse} label])])
      [:span.radiovalinnat
       (doall
         (map-indexed (fn [i valinta]
                        (let [otsikko (nayta valinta)
                              arvo (arvo valinta)]
                          ^{:key otsikko}
                          [:span.radiovalinta
                           [:input {:type "radio"
                                    :value i
                                    :disabled disabled?
                                    :checked (= nykyinen-arvo arvo)
                                    :on-change #(reset! data arvo)}]
                           [:span.radiovalinta-label.klikattava {:on-click #(reset! data arvo)}
                            otsikko]]))
                      valinnat))])))

(defmethod nayta-arvo :radio [{:keys [valinta-nayta]} data]
  [:span ((or valinta-nayta str) @data)])

;; Luo usean checkboksin, jossa valittavissa N-kappaleita vaihtoehtoja. Arvo on setti ruksittuja asioita
(defmethod tee-kentta :checkbox-group
  [{:keys [vaihtoehdot vaihtoehto-nayta valitse-kaikki?
           tyhjenna-kaikki? nayta-rivina? disabloi tasaa
           muu-vaihtoehto muu-kentta palstoja
           valitse-fn valittu-fn]} data]
  (assert data)
  (let [palstoja (or palstoja 1)
        vaihtoehto-nayta (or vaihtoehto-nayta
                             #(clojure.string/capitalize (name %)))
        data-nyt @data
        valitut (if valittu-fn
                  (partial valittu-fn @data)
                  (set (or data-nyt #{})))
        valitse (if valitse-fn
                  valitse-fn
                  (fn [data valinta valittu?]
                    (if valittu?
                      (conj (or data #{}) valinta)
                      (disj data valinta))))]
    [:div.boolean-group
     (when tyhjenna-kaikki?
       [:button.nappi-toissijainen {:on-click #(reset! data (apply disj @data vaihtoehdot))}
        [ikonit/ikoni-ja-teksti [ikonit/livicon-trash] "Tyhjennä kaikki"]])
     (when valitse-kaikki?
       [:button.nappi-toissijainen {:on-click #(swap! data clojure.set/union (into #{} vaihtoehdot))}
        [ikonit/ikoni-ja-teksti [ikonit/livicon-check] "Tyhjennä kaikki"]])
     (let [vaihtoehdot-palstoissa (partition-all
                                    (Math/ceil (/ (count vaihtoehdot) palstoja))
                                    vaihtoehdot)
           coll-luokka (Math/ceil (/ 12 palstoja))
           checkbox (fn [vaihtoehto]
                      (let [valittu? (valitut vaihtoehto)]
                        [:div.checkbox {:class (when nayta-rivina? "checkbox-rivina")}
                         [:label
                          [:input {:type "checkbox" :checked (boolean valittu?)
                                   :disabled (if disabloi
                                               (disabloi valitut vaihtoehto)
                                               false)
                                   :on-change #(swap! data valitse vaihtoehto (not valittu?))}]
                          (vaihtoehto-nayta vaihtoehto)]]))
           checkboxit (doall
                        (for [v vaihtoehdot]
                          ^{:key (str "boolean-group-" (name v))}
                          [checkbox v]))
           checkboxit-palstoissa (doall
                                   (for* [vaihtoehdot-palsta vaihtoehdot-palstoissa]
                                     [:div
                                      [:div (when (> palstoja 1)
                                              {:class (str "col-sm-" coll-luokka)})
                                       (for [v vaihtoehdot-palsta]
                                         ^{:key (str "boolean-group-" (name v))}
                                         [checkbox v])]]))
           muu (when (and muu-vaihtoehto
                          (valitut muu-vaihtoehto))
                 [tee-kentta muu-kentta
                  (atomina muu-kentta data-nyt (partial reset! data))])]
       (if nayta-rivina?
         [:table.boolean-group {:class (when (= tasaa :keskita) "keskita")}
          [:tbody
           [:tr
            (map-indexed (fn [i cb]
                           ^{:key i}
                           [:td cb])
                         checkboxit)
            (when muu
              ^{:key "muu"}
              [:td.muu muu])]]]
         [:span checkboxit-palstoissa
          [:span.muu muu]]))]))


;; Boolean-tyyppinen checkbox, jonka arvo on true tai false
(defmethod tee-kentta :checkbox [{:keys [teksti nayta-rivina?]} data]
  (let [input-id (str "harja-checkbox-" (gensym))
        paivita-valitila #(when-let [node (.getElementById js/document input-id)]
                            (set! (.-indeterminate node)
                                  (= @data ::indeterminate)))]
    (komp/luo
      (komp/piirretty paivita-valitila)
      (komp/kun-muuttui paivita-valitila)
      (fn [{:keys [teksti nayta-rivina? disabled?]} data]
        (let [arvo (if (nil? @data)
                     false
                     @data)]
          [:div.boolean
           (let [checkbox [:div.checkbox
                           [:label {:on-click #(.stopPropagation %)}
                            [:input {:id input-id
                                     :type "checkbox"
                                     :disabled disabled?
                                     :checked arvo
                                     :on-change #(let [valittu? (-> % .-target .-checked)]
                                                   (reset! data valittu?))}]
                            teksti]]]
             (if nayta-rivina?
               [:table.boolean-group
                [:tbody
                 [:tr
                  [:td checkbox]]]]
               checkbox))])))))

(defmethod tee-kentta :radio-group [{:keys [vaihtoehdot vaihtoehto-nayta nayta-rivina?
                                            oletusarvo]} data]
  (let [vaihtoehto-nayta (or vaihtoehto-nayta
                             #(clojure.string/capitalize (name %)))
        valittu (or @data nil)]
    ;; Jos oletusarvo on annettu, se sisältyy vaihtoehtoihin, ja mitään ei ole valittu,
    ;; valitaan oletusarvo
    (when (and (nil? valittu)
               oletusarvo
               (some (partial = oletusarvo) vaihtoehdot))
      (reset! data oletusarvo))
    [:div
     (let [radiobuttonit (doall
                           (for [vaihtoehto vaihtoehdot]
                             ^{:key (str "radio-group-" (name vaihtoehto))}
                             [:div.radio
                              [:label
                               [:input {:type "radio"
                                        ;; Samoin asetetaan checkbox valituksi luontivaiheessa,
                                        ;; jos parametri annettu
                                        :checked (or (and (nil? valittu) (= vaihtoehto oletusarvo))
                                                     (= valittu vaihtoehto))
                                        :on-change #(let [valittu? (-> % .-target .-checked)]
                                                      (if valittu?
                                                        (reset! data vaihtoehto)))}]
                               (vaihtoehto-nayta vaihtoehto)]]))]
       (if nayta-rivina?
         [:table.boolean-group
          [:tr
           (map-indexed (fn [i cb]
                          ^{:key i}
                          [:td cb])
                        radiobuttonit)]]
         radiobuttonit))]))

(defmethod tee-kentta :valinta
  ([{:keys [alasveto-luokka valinta-nayta valinta-arvo
            valinnat valinnat-fn rivi on-focus on-blur jos-tyhja
            jos-tyhja-fn disabled? fokus-klikin-jalkeen?
            nayta-ryhmat ryhmittely ryhman-otsikko]} data]
    ;; valinta-arvo: funktio rivi -> arvo, jolla itse lomakken data voi olla muuta kuin valinnan koko item
    ;; esim. :id
    (assert (or valinnat valinnat-fn) "Anna joko valinnat tai valinnat-fn")
    (let [nykyinen-arvo @data
          valinnat (or valinnat (valinnat-fn rivi))]
      [livi-pudotusvalikko {:class (str "alasveto-gridin-kentta " alasveto-luokka)
                            :valinta (if valinta-arvo
                                       (some #(when (= (valinta-arvo %) nykyinen-arvo) %) valinnat)
                                       nykyinen-arvo)
                            :valitse-fn #(reset! data
                                                 (if valinta-arvo
                                                   (valinta-arvo %)
                                                   %))
                            :fokus-klikin-jalkeen? fokus-klikin-jalkeen?
                            :nayta-ryhmat nayta-ryhmat
                            :ryhmittely ryhmittely
                            :ryhman-otsikko ryhman-otsikko
                            :on-focus on-focus
                            :on-blur on-blur
                            :format-fn (if (empty? valinnat)
                                         (or jos-tyhja-fn (constantly (or jos-tyhja "Ei valintoja")))
                                         (or (and valinta-nayta #(valinta-nayta % true)) str))
                            :disabled disabled?}
       valinnat]))
  ([{:keys [jos-tyhja]} data data-muokkaus-fn]
    ;; HUOM!! Erona 2-arity tapaukseen, valinta-nayta funktiolle annetaan vain yksi argumentti kahden sijasta
    (let [jos-tyhja-default-fn (constantly (or jos-tyhja "Ei valintoja"))]
      (fn [{:keys [alasveto-luokka valinta-nayta valinta-arvo data-cy
                   valinnat valinnat-fn rivi on-focus on-blur jos-tyhja
                   jos-tyhja-fn disabled? fokus-klikin-jalkeen?
                   nayta-ryhmat ryhmittely ryhman-otsikko]} data data-muokkaus-fn]
        (assert (not (satisfies? IDeref data)) "Jos käytät tee-kentta 3 aritylla, data ei saa olla derefable. Tämä sen takia, ettei React turhaan renderöi elementtiä")
        (assert (fn? data-muokkaus-fn) "Data-muokkaus-fn pitäisi olla funktio, joka muuttaa näytettävää dataa jotenkin")
        (assert (or valinnat valinnat-fn) "Anna joko valinnat tai valinnat-fn")
        (let [valinnat (or valinnat (valinnat-fn rivi))]
          [livi-pudotusvalikko {:class (str "alasveto-gridin-kentta " alasveto-luokka)
                                :valinta (if valinta-arvo
                                           (some #(when (= (valinta-arvo %) data) %) valinnat)
                                           data)
                                :valitse-fn data-muokkaus-fn
                                :fokus-klikin-jalkeen? fokus-klikin-jalkeen?
                                :nayta-ryhmat nayta-ryhmat
                                :ryhmittely ryhmittely
                                :ryhman-otsikko ryhman-otsikko
                                :on-focus on-focus
                                :on-blur on-blur
                                :format-fn (if (empty? valinnat)
                                             (or jos-tyhja-fn jos-tyhja-default-fn)
                                             (or valinta-nayta str))
                                :disabled disabled?
                                :data-cy data-cy}
           valinnat])))))

(defmethod nayta-arvo :valinta [{:keys [valinta-nayta valinta-arvo
                                        valinnat valinnat-fn rivi hae
                                        jos-tyhja-fn jos-tyhja]} data]
  (let [nykyinen-arvo @data
        valinnat (or valinnat (valinnat-fn rivi))
        valinta (if valinta-arvo
                  (some #(when (= (valinta-arvo %) nykyinen-arvo) %) valinnat)
                  nykyinen-arvo)]
    [:span (or ((or valinta-nayta str false) valinta) valinta)]
    [:span (if (empty? valinnat)
             ((or jos-tyhja-fn (constantly (or jos-tyhja "Ei valintoja"))) valinta)
             (or ((or valinta-nayta str false) valinta) valinta))]))



(defmethod tee-kentta :kombo [{:keys [valinnat on-focus on-blur lomake? disabled?]} data]
  (let [auki (atom false)]
    (fn [{:keys [valinnat]} data]
      (let [nykyinen-arvo (or @data "")]
        [:div.dropdown {:class (when @auki "open")}
         [:input.kombo {:class (cond-> nil
                                       lomake? (str "form-control ")
                                       disabled? (str "disabled"))
                        :type "text" :value nykyinen-arvo
                        :on-focus on-focus
                        :on-blur on-blur
                        :disabled disabled?
                        :on-change #(reset! data (-> % .-target .-value))}]
         [:button {:on-click #(do (swap! auki not) nil) :disabled disabled?}
          [:span.caret ""]]
         [:ul.dropdown-menu {:role "menu"}
          (for [v (filter #(not= -1 (.indexOf (.toLowerCase (str %)) (.toLowerCase nykyinen-arvo))) valinnat)]
            ^{:key (hash v)}
            [:li {:role "presentation"} [linkki v #(do (reset! data v)
                                                       (reset! auki false))]])]]))))

(defmethod tee-kentta :aikavalitsin [{:keys [pvm kellonaika plusmiinus] :as asetukset} data]
  [:div {:style {:vertical-align "middle"}}
   [:div {:style {:margin-right "5px" :display "inline-block"}}
    [tee-kentta (assoc pvm :tyyppi :pvm)
     (r/wrap
       (:pvm @data)
       #(swap! data assoc :pvm %))]]

   [:div {:style {:width "65px" :display "inline-block" :margin "5px"}}
    [tee-kentta (assoc kellonaika :tyyppi :valinta
                       :valinnat (or (:valinnat kellonaika) ["00:00" "06:00" "12:00" "18:00"])
                       :alasveto-luokka "inline-block")
     (r/wrap
       (:kellonaika @data)
       #(swap! data assoc :kellonaika %))]]

   [:span {:style {:margin-right "3px"}} "\u00B1"]
   [tee-kentta (assoc plusmiinus :tyyppi :positiivinen-numero) (r/wrap
                                                                 (:plusmiinus @data)
                                                                 #(swap! data assoc :plusmiinus %))]])

;; Regexiä käytetään tunnistamaan, millaisia merkkejä pvm-kenttään voi syöttää.
;; Regex sallii esim muotoa ".10.2009" muotoa olevan merkkijonon, koska tällaiseen voidaan helposti
;; päätyä, jos käyttäjä pyyhkii päivän pois validin pvm:n alusta - eikä tätä tietenkään haluta estää.
;; Tämän takia merkkien lukumäärien vaatimukset alkavat aina nollasta.
;; Käytännössä regex sallii vuosiluvut 0-2999
(def +pvm-regex+ #"\d{0,2}((\.\d{0,2})(\.[1-2]{0,1}\d{0,3})?)?")
(def +aika-regex+ #"\d{1,2}(:\d{0,2})?")
;; Kellonajan tuntiosa on joko:
;; numero 0-9 väliltä
;; numero 0-9 väliltä, edessä 0 (00, 01, .., 09)
;; numero 0-9 väliltä, edessä 1 (12,13..)
;; numero 0-3 väliltä, edessä 2 (20,21, .. ,23)
;; minuutit ovat numero 00-59 väliltä.
;; HUOM: 0:0 ei siis ole validi kellonaika. Esim 0:00 on.
(def +validi-aika-regex+ #"^([0-9]|0[0-9]|1[0-9]|2[0-3]):[0-5][0-9]$")


(def key-code-tab 9)
(def key-code-enter 13)

;; pvm-tyhjana ottaa vastaan pvm:n siitä kuukaudesta ja vuodesta, jonka sivu
;; halutaan näyttää ensin
(defmethod tee-kentta :pvm [{:keys [pvm-tyhjana rivi on-focus lomake? pakota-suunta validointi]} data]

  (let [;; pidetään kirjoituksen aikainen ei validi pvm tallessa
        p @data
        teksti (atom (if p
                       (pvm/pvm p)
                       ""))
        validoi-fn (fn [validoi? validointi uusi-paiva]
                     (if validoi?
                       (cond
                         (fn? validointi) (validointi uusi-paiva)
                         (nil? uusi-paiva) false
                         (= :korkeintaan-kuluva-paiva validointi) (pvm/sama-tai-ennen? uusi-paiva (pvm/nyt) true))
                       true))
        ;; picker auki?
        auki (atom false)

        teksti-paivamaaraksi! (fn [validoi-fn data t]
                                (let [d (pvm/->pvm t)
                                      eri-pvm? (not (or (pvm/sama-pvm? @data d)
                                                        (and (nil? d) (nil? @data))))]
                                  (when (validoi-fn d)
                                    (reset! teksti t)
                                    (when eri-pvm?
                                      (reset! data d)))))
        muuta! (fn [data t]
                 (when (or (re-matches +pvm-regex+ t)
                           (str/blank? t))
                   (reset! teksti t))
                 (if (str/blank? t)
                   (reset! data nil)))]
    (komp/luo
      (komp/klikattu-ulkopuolelle #(reset! auki false))
      {:component-will-receive-props
       (fn [this _ {:keys [focus] :as s} data]
         (let [p @data]
           (reset! teksti (if p
                            (pvm/pvm p)
                            ""))))

       :reagent-render
       (fn [{:keys [on-focus on-blur placeholder rivi validointi]} data]
         (let [nykyinen-pvm @data
               nykyinen-teksti @teksti
               pvm-tyhjana (or pvm-tyhjana (constantly nil))
               validoi? (some? validointi)
               validoi (r/partial validoi-fn validoi? validointi)
               naytettava-pvm (or
                                (pvm/->pvm nykyinen-teksti)
                                nykyinen-pvm
                                (pvm-tyhjana rivi))]
           [:span.pvm-kentta
            {:on-click #(do (reset! auki true) nil)
             :style {:display "inline-block"}}
            [:input.pvm {:class (when lomake? "form-control")
                         :placeholder (or placeholder "pp.kk.vvvv")
                         :value nykyinen-teksti
                         :on-focus #(do (when on-focus (on-focus)) (reset! auki true) %)
                         :on-change #(muuta! data (-> % .-target .-value))
                         ;; keycode 9 = Tab. Suljetaan datepicker kun painetaan tabia.
                         :on-key-down #(when (or (= key-code-tab (-> % .-keyCode)) (= key-code-tab (-> % .-which))
                                                 (= key-code-enter (-> % .-keyCode)) (= key-code-enter (-> % .-which)))
                                         (teksti-paivamaaraksi! validoi data nykyinen-teksti)
                                         (reset! auki false)
                                         true)
                         :on-blur #(let [t (-> % .-target .-value)
                                         pvm (pvm/->pvm t)]
                                     (when on-blur
                                       (on-blur %))
                                     (if (and pvm (not (validoi pvm)))
                                       (do (reset! data nil)
                                           (reset! teksti ""))
                                       (teksti-paivamaaraksi! validoi data (-> % .-target .-value))))}]
            (when @auki
              [pvm-valinta/pvm-valintakalenteri {:valitse #(when (validoi %)
                                                             (reset! auki false)
                                                             (reset! data %)
                                                             (reset! teksti (pvm/pvm %)))
                                                 :pvm naytettava-pvm
                                                 :pakota-suunta pakota-suunta
                                                 :valittava?-fn (when validoi?
                                                                  validoi)}])]))})))

(defmethod nayta-arvo :pvm [_ data]
  [:span (if-let [p @data]
           (pvm/pvm p)
           "")])

(defn- resetoi-jos-tyhja-tai-matchaa [t re atomi]
  (when (or (str/blank? t)
            (re-matches re t))
    (reset! atomi t)))

(defn- aseta-aika! [aika-text aseta-fn!]
  (if (re-matches #"\d{3}" aika-text)
    ;; jos yritetään kirjoittaa aika käyttämättä : välimerkkiä,
    ;; niin 3 merkin kohdalla lisätään se automaattisesti
    (let [alku (js/parseInt (.substring aika-text 0 2))]
      (if (< alku 24)
        ;; 123 => 12:3
        (aseta-fn! (str (subs aika-text 0 2) ":" (subs aika-text 2)))
        ;; 645 => 6:45
        (aseta-fn! (str (subs aika-text 0 1) ":" (subs aika-text 1)))))

    (when (or (str/blank? aika-text)
              (re-matches +aika-regex+ aika-text))
      (aseta-fn! aika-text))))

(defmethod tee-kentta :pvm-aika [{:keys [pvm-tyhjana rivi focus on-focus on-blur lomake? pakota-suunta]}
                                 data]

  (let [;; pidetään kirjoituksen aikainen ei validi pvm tallessa
        p @data
        pvm-teksti (atom (if p
                           (pvm/pvm p)
                           ""))
        aika-teksti (atom (if p
                            (pvm/aika p)
                            ""))
        ;; picker auki?
        auki (atom false)
        pvm-aika-koskettu (atom [(not
                                   (or (str/blank? @pvm-teksti) (nil? @pvm-teksti)))
                                 (not
                                   (or (str/blank? @aika-teksti) (nil? @aika-teksti)))])

        aseta-teksti! (fn [p]
                        (if p
                          (do
                            (reset! pvm-teksti (pvm/pvm p))
                            (reset! aika-teksti (pvm/aika p)))
                          (do
                            (reset! pvm-teksti "")
                            (reset! aika-teksti ""))))

        edellinen-arvo (volatile! @data)]

    (komp/luo
      (komp/klikattu-ulkopuolelle #(reset! auki false))

      ;; Kuunnellaan data atomia, jos sen arvoa muutetaan muualla
      ;; päivitetään tekstikenttien sisältö vastaamaan uutta tilaa
      (komp/watcher data (fn [_ vanha uusi]
                           (when-not (= vanha uusi)
                             (aseta-teksti! uusi))))


      ;; Jos data on wrap, verrataan muuttuvaa dataa edellliseen ja päivitetään
      ;; tekstikentät jos muutoksia havaitaan.
      (komp/vanhat-ja-uudet-parametrit
        (fn [[_ vanha-data] [_ uusi-data]]
          (when (not= vanha-data uusi-data)
            ;; Data atomi on muuttunut (kyseessä wrap), päivitä jos on muuttunut edellisestä
            (let [vanha @edellinen-arvo
                  uusi @uusi-data]
              (when (not= vanha uusi)
                (vreset! edellinen-arvo uusi)
                (aseta-teksti! uusi))))))

      ;; Sulje mahdollisesti auki jäänyt datepicker kun focus poistuu
      {:component-will-receive-props
       (fn [this _ {:keys [focus] :as s} data]
         (when-not focus
           (reset! auki false)))}

      (fn [_ data]
        (let [aseta! (fn [force?]
                       (let [pvm @pvm-teksti
                             aika @aika-teksti
                             p (pvm/->pvm-aika (str pvm " " aika))]
                         (when (or force? (not (some false? @pvm-aika-koskettu)))
                           (when p
                             (reset! data p)))))

              muuta-pvm! #(resetoi-jos-tyhja-tai-matchaa % +pvm-regex+ pvm-teksti)
              muuta-aika! #(aseta-aika! % (partial reset! aika-teksti))

              koske-aika! (fn [] (swap! pvm-aika-koskettu assoc 1 true))
              koske-pvm! (fn [] (swap! pvm-aika-koskettu assoc 0 true))

              nykyinen-pvm @data
              nykyinen-pvm-teksti @pvm-teksti
              nykyinen-aika-teksti @aika-teksti
              pvm-tyhjana (or pvm-tyhjana (constantly nil))
              naytettava-pvm (or
                               (pvm/->pvm nykyinen-pvm-teksti)
                               nykyinen-pvm
                               (pvm-tyhjana rivi))]
          [:span.pvm-aika-kentta
           [:table
            [:tbody
             [:tr
              [:td
               [:input.pvm {:class (when lomake? "form-control")
                            :placeholder "pp.kk.vvvv"
                            :on-click #(do (.stopPropagation %)
                                           (.preventDefault %)
                                           (reset! auki true)
                                           %)
                            :value nykyinen-pvm-teksti
                            :on-focus #(do (when on-focus (on-focus)) (reset! auki true) %)
                            :on-change #(muuta-pvm! (-> % .-target .-value))
                            ;; keycode 9 = Tab. Suljetaan datepicker kun painetaan tabia.
                            :on-key-down #(when (or (= 9 (-> % .-keyCode)) (= 9 (-> % .-which)))
                                            (reset! auki false)
                                            %)
                            :on-blur #(do (when on-blur (on-blur %)) (koske-pvm!) (aseta! false) %)}]
               (when @auki
                 [pvm-valinta/pvm-valintakalenteri {:valitse #(do (reset! auki false)
                                                                  (muuta-pvm! (pvm/pvm %))
                                                                  (koske-pvm!)
                                                                  (aseta! true))
                                                    :pvm naytettava-pvm
                                                    :pakota-suunta pakota-suunta}])]
              [:td
               [:input {:class (str (when lomake? "form-control")
                                    (when (and (not (re-matches +validi-aika-regex+
                                                                nykyinen-aika-teksti))
                                               (pvm/->pvm nykyinen-pvm-teksti))
                                      " puuttuva-arvo"))
                        :placeholder "tt:mm"
                        :size 5 :max-length 5
                        :value nykyinen-aika-teksti
                        :on-change #(muuta-aika! (-> % .-target .-value))
                        :on-blur #(do (koske-aika!) (aseta! false))}]]]]]])))))

(defmethod nayta-arvo :pvm-aika [_ data]
  [:span (if-let [p @data]
           (pvm/pvm-aika p)
           "")])

(defmethod tee-kentta :spinner [{:keys [viesti opts]}]
  [ajax-loader (or viesti "Lataa") opts])

(defmethod tee-kentta :tyhja [{:keys [viesti opts]}]
  [:span.tyhja])

(defn hae-tr-geometria [osoite hakufn tr-osoite-ch virheet]
  (go
    (log "Haetaan geometria osoitteelle: " (pr-str osoite))
    (let [tulos (<! (hakufn osoite))]
      (log "Saatiin tulos: " (pr-str tulos))
      (if-not (or (nil? tulos) (k/virhe? tulos))
        (do
          (>! tr-osoite-ch (assoc osoite :geometria tulos))
          (reset! virheet nil))
        (do
          (>! tr-osoite-ch :virhe)
          (reset! virheet "Reitille ei löydy tietä."))))))

(defn- onko-tr-osoite-kokonainen? [osoite]
  (every? #(get osoite %) [:numero :alkuosa :alkuetaisyys :loppuosa :loppuetaisyys]))

(defn- onko-tr-osoite-pistemainen? [osoite]
  (every? #(get osoite %) [:numero :alkuosa :alkuetaisyys]))

(defn hae-tr [tr-osoite-ch virheet osoite]
  (cond
    (onko-tr-osoite-kokonainen? osoite)
    (hae-tr-geometria osoite vkm/tieosoite->viiva tr-osoite-ch virheet)

    (onko-tr-osoite-pistemainen? osoite)
    (hae-tr-geometria osoite vkm/tieosoite->piste tr-osoite-ch virheet)
    :else
    (do
      (tasot/poista-geometria! :tr-valittu-osoite)
      (reset! virheet nil))))

(defn tr-kentan-elementti
  ([lomake? muuta! blur placeholder value key disabled?] (tr-kentan-elementti lomake? muuta! blur placeholder value key disabled? (str "tr-" (name key))))
  ([lomake? muuta! blur placeholder value key disabled? luokat]
   [:input.tierekisteri {:class (str
                                  luokat " "
                                  (when lomake? "form-control ")
                                  (when disabled? "disabled "))
                         :size 5 :max-length 10
                         :placeholder placeholder
                         :value value
                         :disabled disabled?
                         :on-change (muuta! key)
                         :on-blur blur}]))

(defn piste-tai-eka [arvo]
  (if (vector? (:geometria arvo))
    (first (:geometria arvo))
    (:geometria arvo)))

(defn tr-valintanapin-teksti [alkuperainen nykyinen]
  (let [molemmat-tyhjat? (and (empty? alkuperainen) (empty? nykyinen))
        muuttumaton? (= alkuperainen nykyinen)]
    (cond
      molemmat-tyhjat? " Valitse sijainti"
      muuttumaton? " Muokkaa reittiä"
      :else " Muuta valintaa")))

(defn- tierekisterikentat-table [pakollinen? tie aosa aet losa loppuet tr-otsikot? sijainnin-tyhjennys karttavalinta virhe
                                 piste? vaadi-vali?]
  [:table
   (when tr-otsikot?
     [:thead
      [:tr
       [:th
        [:span "Tie"]
        (when pakollinen? [:span.required-tahti " *"])]
       [:th
        [:span "aosa"]
        (when pakollinen? [:span.required-tahti " *"])]
       [:th
        [:span "aet"]
        (when pakollinen? [:span.required-tahti " *"])]
       (when (not piste?)
         [:th
          [:span "losa"]
          (when vaadi-vali? [:span.required-tahti " *"])])
       (when (not piste?)
         [:th
          [:span "let"]
          (when vaadi-vali? [:span.required-tahti " *"])])]])
   [:tbody
    [:tr
     [:td tie]
     [:td aosa]
     [:td aet]
     (when (not piste?)
       [:td losa])
     (when (not piste?)
       [:td loppuet])
     (when sijainnin-tyhjennys
       [:td.sijannin-tyhjennys
        sijainnin-tyhjennys])
     [:td.karttavalinta
      karttavalinta]
     (when virhe
       [:td virhe])]]])

(defn- tierekisterikentat-rivitetty
  "Erilainen tyyli TR valitsimelle, jos lomake on hyvin kapea.
  Rivittää tierekisterivalinnan usealle riville."
  [pakollinen? tie aosa aet losa loppuet tr-otsikot? sijainnin-tyhjennys karttavalinta virhe]
  [:table
   [:tbody
    [:tr
     [:td {:colSpan 2}
      [:label.control-label [:span.kentan-label "Tie"]]]]
    [:tr
     [:td {:colSpan 2}
      tie]]
    [:tr
     [:td
      [:label.control-label [:span.kentan-label "Alkuosa"]]]
     [:td
      [:label.control-label [:span.kentan-label "Alkuetäisyys"]]]]
    [:tr
     [:td aosa] [:td aet]]
    [:tr
     [:td [:label.control-label [:span.kentan-label "Loppuosa"]]]
     [:td [:label.control-label [:span.kentan-label "Loppuetäisyys"]]]]
    [:tr
     [:td losa] [:td loppuet]]
    (when sijainnin-tyhjennys
      [:tr [:td.sijannin-tyhjennys
            sijainnin-tyhjennys]])
    [:tr
     [:td {:colSpan 2} karttavalinta]]
    (when virhe
      [:tr
       [:td {:colSpan 2} virhe]])]])


(def ^:const tr-osoite-domain-avaimet [::trd/tie ::trd/aosa ::trd/aet ::trd/losa ::trd/let])
(def ^:const tr-osoite-raaka-avaimet [:numero :alkuosa :alkuetaisyys :loppuosa :loppuetaisyys])

(defmethod tee-kentta :tierekisteriosoite [{:keys [tyyli lomake? ala-nayta-virhetta-komponentissa?
                                                   sijainti pakollinen? tyhjennys-sallittu? piste? vaadi-vali?
                                                   avaimet voi-valita-kartalta?]} data]
  (let [osoite-alussa @data
        voi-valita-kartalta? (if (some? voi-valita-kartalta?)
                               voi-valita-kartalta?
                               true)
        hae-sijainti (not (nil? sijainti))                  ;; sijainti (ilman deref!!) on nil tai atomi. Nil vain jos on unohtunut?
        tr-osoite-ch (chan)

        virheet (atom nil)

        alkuperainen-sijainti (atom (when sijainti @sijainti))

        osoite-ennen-karttavalintaa (atom nil)
        sijainti-ennen-karttavalintaa (atom nil)

        karttavalinta-kaynnissa? (atom false)

        keskita-kartta! (fn [sijainti]
                          (when sijainti
                            (kartta/keskita-kartta-alueeseen! (harja.geo/extent sijainti))))

        ;; Tämä tarvitaan, koska sijainti voi olla wrap eikä oikea
        ;; atomi, joten se pitää joka updatessa päivittää
        sijainti-atom (volatile! sijainti)

        nayta-kartalla (fn [arvo]
                         (if (or (nil? arvo) (vkm/virhe? arvo))
                           (tasot/poista-geometria! :tr-valittu-osoite)
                           (when-not (= arvo @alkuperainen-sijainti)
                             (do
                               (tasot/nayta-geometria!
                                 :tr-valittu-osoite
                                 {:alue (maarittele-feature
                                          arvo
                                          false
                                          asioiden-ulkoasu/tr-ikoni
                                          asioiden-ulkoasu/tr-viiva)
                                  :type :tr-valittu-osoite})
                               (keskita-kartta! arvo)))))

        hae-tr (if avaimet
                 (fn [tr-osoite-ch virheet osoite]
                   (hae-tr tr-osoite-ch virheet
                           (zipmap tr-osoite-raaka-avaimet
                                   (map #(when osoite (osoite %)) avaimet))))
                 hae-tr)

        tee-tr-haku (partial hae-tr tr-osoite-ch virheet)]
    (when hae-sijainti
      (nayta-kartalla @sijainti)
      (go-loop []
               (when-let [arvo (<! tr-osoite-ch)]
                 (log "VKM/TR: " (pr-str arvo))
                 (reset! @sijainti-atom
                         (if-not (= arvo :virhe)
                           (do (nappaa-virhe (nayta-kartalla (piste-tai-eka arvo)))
                               (piste-tai-eka arvo))
                           (do
                             (tasot/poista-geometria! :tr-valittu-osoite)
                             nil)))
                 (recur))))

    (komp/luo
      (komp/vanhat-ja-uudet-parametrit
        (fn [[_ vanha-osoite-atom :as vanhat] [_ uusi-osoite-atom :as uudet]]
          (when (not= @vanha-osoite-atom @uusi-osoite-atom)
            (tee-tr-haku @uusi-osoite-atom))))
      (komp/kun-muuttuu
        (fn [{sijainti :sijainti} _]
          (if-not sijainti
            (tasot/poista-geometria! :tr-valittu-osoite)
            (do (reset! alkuperainen-sijainti @sijainti)
                (vreset! sijainti-atom sijainti)
                (nayta-kartalla @sijainti)))))
      (when voi-valita-kartalta?
        (komp/kuuntelija :kartan-koko-vaihdettu #(when-let [sijainti-atom @sijainti-atom]
                                                   (keskita-kartta! @sijainti-atom))))

      (komp/ulos #(do
                    (log "Lopetetaan TR sijaintipäivitys")
                    (async/close! tr-osoite-ch)
                    (when voi-valita-kartalta?
                      (reset! kartta/pida-geometriat-nakyvilla? kartta/pida-geometria-nakyvilla-oletusarvo)
                      (tasot/poista-geometria! :tr-valittu-osoite)
                      (kartta/zoomaa-geometrioihin))))

      (fn [{:keys [tyyli lomake? sijainti piste? vaadi-vali? tr-otsikot?]} data]
        (let [avaimet (or avaimet tr-osoite-raaka-avaimet)
              _ (assert (= 5 (count avaimet))
                        (str "TR-osoitekenttä tarvii 5 avainta (tie,aosa,aet,losa,let), saatiin: "
                             (count avaimet)))
              tr-otsikot? (if (nil? tr-otsikot?)
                            true
                            tr-otsikot?)
              [numero-avain alkuosa-avain alkuetaisyys-avain loppuosa-avain loppuetaisyys-avain]
              avaimet

              tierekisterikentat (if (= tyyli :rivitetty)
                                   tierekisterikentat-rivitetty
                                   tierekisterikentat-table)

              osoite @data

              [numero alkuosa alkuetaisyys loppuosa loppuetaisyys]
              (map #(when osoite (osoite %)) avaimet)

              muuta! (fn [kentta]
                       #(let [v (-> % .-target .-value)
                              tr (swap! data assoc kentta (when (and (not (= "" v))
                                                                     (re-matches #"\d*" v))
                                                            (js/parseInt (-> % .-target .-value))))]))
              blur (when hae-sijainti
                     #(tee-tr-haku osoite))
              normalisoi (fn [{:keys [numero alkuosa alkuetaisyys loppuosa loppuetaisyys]}]
                           {numero-avain numero
                            alkuosa-avain alkuosa
                            alkuetaisyys-avain alkuetaisyys
                            loppuosa-avain loppuosa
                            loppuetaisyys-avain loppuetaisyys})]
          [:span.tierekisteriosoite-kentta (when @virheet {:class "sisaltaa-virheen"})
           (when (and @virheet (false? ala-nayta-virhetta-komponentissa?))
             [:div {:class "virheet"}
              [:div {:class "virhe"}
               [:span (ikonit/livicon-warning-sign) [:span @virheet]]]])

           [tierekisterikentat
            pakollinen?
            [tr-kentan-elementti lomake? muuta! blur
             "Tie" numero numero-avain @karttavalinta-kaynnissa?]
            [tr-kentan-elementti lomake? muuta! blur
             "aosa" alkuosa alkuosa-avain @karttavalinta-kaynnissa?]
            [tr-kentan-elementti lomake? muuta! blur
             "aet" alkuetaisyys alkuetaisyys-avain @karttavalinta-kaynnissa?]
            [tr-kentan-elementti lomake? muuta! blur
             "losa" loppuosa loppuosa-avain @karttavalinta-kaynnissa?]
            [tr-kentan-elementti lomake? muuta! blur
             "let" loppuetaisyys loppuetaisyys-avain @karttavalinta-kaynnissa?]
            tr-otsikot?
            (when (and (not @karttavalinta-kaynnissa?) tyhjennys-sallittu? voi-valita-kartalta?)
              [napit/poista nil
               #(do (tasot/poista-geometria! :tr-valittu-osoite)
                    (reset! data {})
                    (reset! @sijainti-atom nil)
                    (reset! virheet nil))
               {:luokka "nappi-tyhjenna"
                :disabled (empty? @data)}])

            (when voi-valita-kartalta?
              (if-not @karttavalinta-kaynnissa?
                [napit/yleinen-ensisijainen
                 (tr-valintanapin-teksti osoite-alussa osoite)
                 #(do
                    (reset! osoite-ennen-karttavalintaa osoite)
                    (when-let [sijainti @sijainti-atom]
                      (reset! sijainti-ennen-karttavalintaa @sijainti))
                    (reset! data {})
                    (reset! karttavalinta-kaynnissa? true))
                 {:ikoni (ikonit/map-marker)}]
                [tr/karttavalitsin
                 {:kun-peruttu #(do
                                  (reset! data @osoite-ennen-karttavalintaa)
                                  (when-let [sijainti @sijainti-atom]
                                    (reset! sijainti @sijainti-ennen-karttavalintaa))
                                  (reset! karttavalinta-kaynnissa? false))
                  :paivita #(swap! data merge (normalisoi %))
                  :kun-valmis #(do
                                 (reset! data (normalisoi %))
                                 (reset! karttavalinta-kaynnissa? false)
                                 (log "Saatiin tr-osoite! " (pr-str %))
                                 (go (>! tr-osoite-ch %)))}]))

            (when-let [sijainti (and hae-sijainti sijainti @sijainti)]
              (when (vkm/virhe? sijainti)
                [:div.virhe (vkm/pisteelle-ei-loydy-tieta sijainti)]))
            piste?
            vaadi-vali?]])))))

(defmethod tee-kentta :sijaintivalitsin
  ;; Tekee napit paikannukselle ja sijainnin valitsemiselle kartalta.
  ;; Optioilla voidaan asettaa vain toinen valinta mahdolliseksi.
  [{:keys [karttavalinta? paikannus? paikannus-kaynnissa?-atom
           paikannus-onnistui-fn paikannus-epaonnistui-fn
           karttavalinta-tehty-fn poista-valinta? disabled?]} data]
  (let [karttavalinta? (if (some? karttavalinta?) karttavalinta? true)
        paikannus? (if (some? paikannus?) paikannus? true)

        paikannus-kaynnissa? (atom false)

        karttavalinta-kaynnissa? (atom false)]
    (when paikannus-kaynnissa?-atom
      (add-watch paikannus-kaynnissa?
                 :paikannus?
                 (fn [avain ref vanha uusi]
                   (reset! paikannus-kaynnissa?-atom uusi))))

    (komp/luo
      (komp/sisaan #(do
                      (if (nil? @data)
                        (reset! sijaintivalitsin-tiedot/valittu-sijainti nil)
                        (reset! sijaintivalitsin-tiedot/valittu-sijainti {:sijainti @data}))))
      (komp/ulos #(karttatasot/taso-pois! :sijaintivalitsin))
      (fn [{disabled? :disabled?} data]
        (let [vanha-sijainti (:sijainti @data)
              paikannus-onnistui-fn (or paikannus-onnistui-fn
                                        (fn [sijainti]
                                          (let [coords (.-coords sijainti)
                                                koordinaatit {:x (.-longitude coords)
                                                              :y (.-latitude coords)}]
                                            (go (let [piste (<! (k/post! :hae-piste-kartalle koordinaatit))]
                                                  (if (k/virhe? piste)
                                                    (reset! data {:virhe "Pisteen haku epäonnistui"})
                                                    (do (if (= :kayta-lomakkeen-atomia karttavalinta-tehty-fn)
                                                          (reset! data piste)
                                                          (karttavalinta-tehty-fn piste))
                                                        (reset! sijaintivalitsin-tiedot/valittu-sijainti {:sijainti piste}))))))))
              paikannus-epaonnistui-fn (or paikannus-epaonnistui-fn
                                           (fn [virhe]
                                             (reset! data {:virhe "Paikannus epäonnistui"})))
              lopeta-paikannus #(reset! paikannus-kaynnissa? false)
              aloita-paikannus (fn [] (reset! paikannus-kaynnissa? true)
                                 (geo/nykyinen-geolokaatio
                                   #(do (lopeta-paikannus)
                                        (when (not= vanha-sijainti %)
                                          (karttatasot/taso-paalle! :sijaintivalitsin))
                                        (paikannus-onnistui-fn %))
                                   #(do (lopeta-paikannus)
                                        (paikannus-epaonnistui-fn %))))
              lopeta-karttavalinta #(reset! karttavalinta-kaynnissa? false)
              aloita-karttavalinta (fn []
                                     (reset! karttavalinta-kaynnissa? true))]
          [:div
           (when (and paikannus?
                      (geo/geolokaatio-tuettu?))
             [napit/yleinen-ensisijainen
              "Paikanna"
              #(when-not @paikannus-kaynnissa?
                 (aloita-paikannus))
              {:disabled (or @paikannus-kaynnissa? @karttavalinta-kaynnissa? disabled?)
               :ikoni (ikonit/screenshot)
               :tallennus-kaynnissa? @paikannus-kaynnissa?}])

           (when karttavalinta?
             (if-not @karttavalinta-kaynnissa?
               [napit/yleinen-ensisijainen
                "Valitse kartalta"
                #(when-not @karttavalinta-kaynnissa?
                   (aloita-karttavalinta))
                {:disabled (or @paikannus-kaynnissa? @karttavalinta-kaynnissa? disabled?)
                 :ikoni (ikonit/map-marker)}]
               [sijaintivalitsin/sijaintivalitsin {:kun-peruttu #(lopeta-karttavalinta)
                                                   :kun-valmis #(do
                                                                  (lopeta-karttavalinta)
                                                                  (when (not= vanha-sijainti %)
                                                                    (karttatasot/taso-paalle! :sijaintivalitsin))
                                                                  (if (= :kayta-lomakkeen-atomia karttavalinta-tehty-fn)
                                                                    (reset! data {:type :point :coordinates %})
                                                                    (karttavalinta-tehty-fn
                                                                      {:type :point :coordinates %})))}]))
           (when (and poista-valinta?
                      (not @karttavalinta-kaynnissa?)
                      (not @paikannus-kaynnissa?)
                      (not (nil? @data))
                      (not (contains? @data :virhe)))
             [napit/poista
              "Poista valinta"
              (fn [e]
                (reset! sijaintivalitsin-tiedot/valittu-sijainti nil)
                (reset! data nil))
              {:disabled disabled?}])])))))

(defmethod nayta-arvo :tierekisteriosoite [_ data]
  (let [{:keys [numero alkuosa alkuetaisyys loppuosa loppuetaisyys]} @data
        loppu? (or loppuosa loppuetaisyys)]
    [:span.tierekisteriosoite
     [:span.tie "Tie " numero] " / "
     [:span.alkuosa alkuosa] " / "
     [:span.alkuetaisyys alkuetaisyys]
     (when loppu?
       [:span
        " / "
        [:span.loppuosa loppuosa] " / "
        [:span.loppuetaisyys loppuetaisyys]])]))

(defn tee-otsikollinen-kentta [{:keys [otsikko kentta-params arvo-atom luokka tyylit]}]
  [:span {:class (or luokka "label-ja-kentta")
          :style tyylit}
   [:span.kentan-otsikko otsikko]
   [:div.kentta
    [tee-kentta kentta-params arvo-atom]]])

(defn tee-otsikko-ja-kentat [{:keys [otsikko luokka kentat]}]
  [:span {:class (or luokka "label-ja-kentta")}
   [:span.kentan-otsikko otsikko]
   [:span
    (for* [{:keys [kentta-params arvo-atom] :as kentta} kentat]
      [:div.kentta
       [tee-kentta kentta-params arvo-atom]])]])

(defn nayta-otsikollinen-kentta [{:keys [otsikko kentta-params arvo-atom luokka]}]
  [:span {:class (or luokka "label-ja-kentta")}
   [:span.kentan-otsikko otsikko]
   [:div.kentta
    [nayta-arvo kentta-params arvo-atom]]])

(def aika-pattern #"^(\d{1,2})(:(\d{1,2}))(:(\d{1,2}))?$")

(defn- parsi-aika [string]
  (let [[_ t _ m _ s] (re-matches aika-pattern string)]
    (if t
      (pvm/map->Aika {:tunnit (js/parseInt t)
                      :minuutit (js/parseInt m)
                      :sekunnit (and s (js/parseInt s))})
      (pvm/map->Aika {:keskenerainen string}))))

(defn normalisoi-aika-teksti
  "Rajaa annetun käyttäjän text input syötteen aika kenttään sopivaksi.
  Trimmaa, poistaa muut kuin numerot ja kaksoispisteet sekä leikkaa viiteen kirjaimeen."
  [t]
  (let [t (-> t str/trim (str/replace #"[^\d:]" ""))]
    (if (> (count t) 5)
      (subs t 0 5)
      t)))

(defmethod tee-kentta :aika [{:keys [placeholder on-focus on-blur lomake?] :as opts} data]
  (let [{:keys [tunnit minuutit sekunnit keskenerainen] :as aika} @data]
    [:input {:class (str (when lomake? "form-control")
                         (when-not (:tunnit @data) " puuttuva-arvo"))
             :placeholder placeholder
             :on-change (fn [e]
                          (let [v1 (-> e .-target .-value)
                                [v aika] (aseta-aika! v1 (juxt identity parsi-aika))]
                            (if-not aika
                              (swap! data assoc :keskenerainen (normalisoi-aika-teksti v1))
                              (if (:tunnit aika)
                                (swap! data
                                       (fn [aika-nyt]
                                         (pvm/map->Aika
                                           (merge aika-nyt
                                                  (assoc aika :keskenerainen v)))))
                                (swap! data assoc
                                       :tunnit nil
                                       :minuutit nil
                                       :sekunnit nil
                                       :keskenerainen v)))))
             :on-focus on-focus
             :on-blur #(do
                         (when on-blur
                           (on-blur %))
                         (when-let [t (:keskenerainen @data)]
                           (when (and (re-matches #"\d+" t)
                                      (<= 0 (js/parseInt t) 23))
                             (reset! data (pvm/->Aika (js/parseInt t) 0 nil)))))
             :value (or keskenerainen (fmt/aika aika))}]))

(defmethod tee-kentta :toggle [{:keys [paalle-teksti pois-teksti toggle!]} data]
  (assert (and paalle-teksti pois-teksti)
          "Määrittele :paalle-teksti ja :pois-teksti kentät!")
  (let [arvo-nyt @data]
    [napit/yleinen-toissijainen (if arvo-nyt
                                  pois-teksti
                                  paalle-teksti)
     (or toggle! #(swap! data not))
     {:luokka "btn-xs"}]))
