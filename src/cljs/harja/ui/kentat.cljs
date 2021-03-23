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
            [harja.ui.yleiset :refer [linkki ajax-loader livi-pudotusvalikko nuolivalinta valinta-ul-max-korkeus-px]]
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
            [taoensso.timbre :as log]
            [harja.loki :as loki]
            [clojure.string :as string])
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
        hae-kun-yli-n-merkkia (or hae-kun-yli-n-merkkia 2)]
    (komp/luo
      (komp/klikattu-ulkopuolelle #(reset! tulokset nil))

      (fn [_ data]
        [:div.hakukentta.dropdown {:class (when (some? @tulokset) "open")}

         [:input {:class       (cond-> nil
                                       lomake? (str "form-control ")
                                       disabled? (str "disabled"))
                  :value       @teksti
                  :placeholder placeholder
                  :disabled    disabled?
                  :size        pituus
                  :on-change   #(when (= (.-activeElement js/document) (.-target %))
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

         [:ul.hakukentan-lista.dropdown-menu {:role  "menu"
                                              :style {:max-height valinta-ul-max-korkeus-px}}
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

(defmethod tee-kentta :string [{:keys [nimi pituus-max vayla-tyyli? pituus-min virhe? regex focus on-focus on-blur lomake? toiminta-f disabled? vihje]
                                :as   kentta} data]
  [:input {:class       (cond-> nil
                                (and lomake?
                                     (not vayla-tyyli?)) (str "form-control ")
                                vayla-tyyli? (str "input-" (if virhe? "error-" "") "default komponentin-input ")
                                disabled? (str "disabled"))
           :placeholder (placeholder kentta data)
           :on-change   #(let [v (-> % .-target .-value)]
                           (when (or (not regex) (re-matches regex v))
                             (reset! data v)
                             (when toiminta-f
                               (toiminta-f v))))
           :disabled    disabled?
           :on-focus    on-focus
           :on-blur     on-blur
           :value       @data
           :max-length  pituus-max}])

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
         [:textarea {:value       @data
                     :on-change   #(muuta! data %)
                     :on-focus    on-focus
                     :on-blur     on-blur
                     :disabled    disabled?
                     :cols        (or koko-sarakkeet 80)
                     :rows        @rivit
                     :class       (cond-> nil
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

(defmethod tee-kentta :numero [{:keys [oletusarvo validoi-kentta-fn]   :as kentta} data]
  (let [fmt (or
              (when-let [tarkkuus (:desimaalien-maara kentta)]
                #(fmt/desimaaliluku-opt % tarkkuus))
              (:fmt kentta) str)
        teksti (atom nil)
        kokonaisosan-maara (or (:kokonaisosan-maara kentta) 10)]
    (komp/luo
      (komp/nimi "Numerokenttä")
      (komp/piirretty #(when (and oletusarvo (nil? @data)) (reset! data oletusarvo)))
      (fn [{:keys [lomake? kokonaisluku? vaadi-ei-negatiivinen? toiminta-f on-blur on-focus disabled?
                   vayla-tyyli? virhe? yksikko validoi-kentta-fn] :as kentta} data]
        (let [nykyinen-data @data
              nykyinen-teksti (or @teksti
                                  (normalisoi-numero (fmt nykyinen-data))
                                  "")
              kokonaisluku-re-pattern (re-pattern (str "-?\\d{1," kokonaisosan-maara "}"))
              desimaaliluku-re-pattern (re-pattern (str "-?\\d{1," kokonaisosan-maara "}((\\.|,)\\d{0,"
                                                        (or (:desimaalien-maara kentta) +desimaalin-oletus-tarkkuus+)
                                                        "})?"))]
          [:span.numero
           [:input {:class (cond-> nil
                                   (and lomake?
                                        (not vayla-tyyli?)) (str "form-control ")
                                   vayla-tyyli? (str "input-" (if virhe? "error-" "") "default komponentin-input ")
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
                                  (when (and
                                          (or (nil? validoi-kentta-fn)
                                              (validoi-kentta-fn v))
                                          (or (= v "")
                                              (when-not vaadi-ei-negatiivinen? (= v "-"))
                                              ;; Halutaan että käyttäjä voi muokata desimaaliluvun esim ",0" muotoon,
                                              ;; mutta tätä välivaihetta ei tallenneta dataan
                                              (re-matches #"[0-9,.-]+" v)))
                                    (reset! teksti v)

                                    (let [numero (if kokonaisluku?
                                                   (js/parseInt v)
                                                   (js/parseFloat (str/replace v #"," ".")))]
                                      (if (not (js/isNaN numero))
                                        (reset! data numero)
                                        (reset! data nil))
                                      (when toiminta-f
                                        (toiminta-f (when-not (js/isNaN numero)
                                                      numero))))))}]
           (when (and yksikko vayla-tyyli?)
             [:span.sisainen-label {:style {:margin-left (* -1 (+ 25 (* (- (count yksikko) 2) 5)))}} yksikko])])))))

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
      [:input {:class       (cond-> nil
                                    lomake? (str "form-control ")
                                    disabled? (str "disabled"))
               :placeholder placeholder
               :disabled    disabled?
               :type        "text"
               :value       @teksti
               :on-change   #(let [txt (-> % .-target .-value)]
                               (when (re-matches pattern txt)
                                 (reset! teksti txt)
                                 (reset! data (big/parse txt))))
               :on-blur     #(reset! teksti (some-> @data fmt))}])))

(defmethod nayta-arvo :big [{:keys [desimaalien-maara]} data]
  [:span (some-> @data (big/fmt desimaalien-maara))])

(defmethod tee-kentta :email [{:keys [on-focus on-blur lomake? disabled?] :as kentta} data]
  [:input {:class     (cond-> nil
                              lomake? (str "form-control ")
                              disabled? (str "disabled"))
           :type      "email"
           :value     @data
           :disabled  disabled?
           :on-focus  on-focus
           :on-blur   on-blur
           :on-change #(reset! data (-> % .-target .-value))}])



(defmethod tee-kentta :puhelin [{:keys [on-focus on-blur pituus lomake? placeholder disabled?] :as kentta} data]
  [:input {:class       (cond-> nil
                                lomake? (str "form-control ")
                                disabled? (str "disabled"))
           :type        "tel"
           :value       @data
           :max-length  pituus
           :disabled    disabled?
           :on-focus    on-focus
           :on-blur     on-blur
           :placeholder placeholder
           :on-change   #(let [uusi (-> % .-target .-value)]
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
        [:span {:style    {:width "100%" :height "100%" :display "inline-block"}
                :on-click valitse}
         [:input {:type      "radio"
                  :value     1
                  :disabled  disabled?
                  :checked   (= nykyinen-arvo arvo)
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
                           [:input {:type      "radio"
                                    :value     i
                                    :disabled  disabled?
                                    :checked   (= nykyinen-arvo arvo)
                                    :on-change #(reset! data arvo)}]
                           [:span.radiovalinta-label.klikattava {:on-click #(reset! data arvo)}
                            otsikko]]))
                      valinnat))])))

(defmethod nayta-arvo :radio [{:keys [valinta-nayta]} data]
  [:span ((or valinta-nayta str) @data)])

(defn- vayla-checkbox
  [{:keys [input-id disabled? arvo data teksti valitse!]}]
  (let [input-id (or input-id
                     (gensym "checkbox-input-id-"))]
    [:div.flex-row
     [:input.vayla-checkbox
      {:id        input-id
       :class     "check"
       :type      "checkbox"
       :disabled  disabled?
       :checked   arvo
       :on-change (or valitse!
                      #(let [valittu? (-> % .-target .-checked)]
                         (reset! data valittu?)))}]
     [:label {:on-click #(.stopPropagation %)
              :for      input-id}
      teksti]]))

;; Luo usean checkboksin, jossa valittavissa N-kappaleita vaihtoehtoja. Arvo on setti ruksittuja asioita
(defmethod tee-kentta :checkbox-group
  [{:keys [vaihtoehdot vaihtoehto-nayta valitse-kaikki?
           tyhjenna-kaikki? nayta-rivina? disabloi tasaa
           muu-vaihtoehto muu-kentta palstoja rivi-solun-tyyli
           valitse-fn valittu-fn vayla-tyyli?]} data]
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
        [ikonit/ikoni-ja-teksti [ikonit/livicon-check] "Valitse kaikki"]])
     (let [vaihtoehdot-palstoissa (partition-all
                                    (Math/ceil (/ (count vaihtoehdot) palstoja))
                                    vaihtoehdot)
           coll-luokka (Math/ceil (/ 12 palstoja))
           checkbox (if vayla-tyyli?
                      (fn [vaihtoehto]
                        [vayla-checkbox {:arvo      (valitut vaihtoehto)
                                         :teksti    (vaihtoehto-nayta vaihtoehto)
                                         :disabled? (if disabloi
                                                      (disabloi valitut vaihtoehto)
                                                      false)
                                         :valitse!  #(swap! data valitse vaihtoehto (not (valitut vaihtoehto)))}])
                      (fn [vaihtoehto]
                        (let [valittu? (valitut vaihtoehto)]
                          [:div.checkbox {:class (when nayta-rivina? "checkbox-rivina")}
                           [:label
                            [:input {:type      "checkbox" :checked (boolean valittu?)
                                     :disabled  (if disabloi
                                                  (disabloi valitut vaihtoehto)
                                                  false)
                                     :on-change #(swap! data valitse vaihtoehto (not valittu?))}]
                            (vaihtoehto-nayta vaihtoehto)]])))
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
                           [:td (when rivi-solun-tyyli
                                     {:style rivi-solun-tyyli})
                            cb])
                         checkboxit)
            (when muu
              ^{:key "muu"}
              [:td.muu muu])]]]
         [:span
          checkboxit-palstoissa
          [:span.muu muu]]))]))


;; Boolean-tyyppinen checkbox, jonka arvo on true tai false
(defmethod tee-kentta :checkbox [{:keys [teksti nayta-rivina? label-luokka vayla-tyyli?]} data]
  (let [input-id (str "harja-checkbox-" (gensym))
        paivita-valitila #(when-let [node (.getElementById js/document input-id)]
                            (set! (.-indeterminate node)
                                  (= @data ::indeterminate)))]
    (komp/luo
      (komp/piirretty paivita-valitila)
      (komp/kun-muuttui paivita-valitila)
      (fn [{:keys [teksti nayta-rivina? label-luokka vayla-tyyli? disabled?
                   iso-clickalue?]} data]
        (let [arvo (if (nil? @data)
                     false
                     @data)]
          [:div.boolean {:style {:padding (when iso-clickalue?
                                            "14px")}
                         :on-click (when iso-clickalue?
                                     #(do
                                        (.stopPropagation %)
                                        (swap! data not)))}
           (let [checkbox (if vayla-tyyli?
                            (vayla-checkbox {:data      data
                                             :input-id  input-id
                                             :teksti    teksti
                                             :disabled? disabled?
                                             :arvo      arvo})
                            [:div.checkbox
                             [:label {:class label-luokka
                                      :on-click #(.stopPropagation %)}
                              [:input {:id        input-id
                                       :type      "checkbox"
                                       :disabled  disabled?
                                       :checked   arvo
                                       :on-change #(let [valittu? (-> % .-target .-checked)]
                                                     (reset! data valittu?))}]
                              teksti]])]
             (if nayta-rivina?
               [:table.boolean-group
                [:tbody
                 [:tr
                  [:td checkbox]]]]
               checkbox))])))))

(defn- vayla-radio [{:keys [id teksti ryhma valittu? oletus-valittu? disabloitu? muutos-fn]}]
  ;; React-varoitus korjattu: saa olla vain checked vai default-checked, ei molempia
  (let [checked (if oletus-valittu?
                  {:default-checked oletus-valittu?}
                  {:checked valittu?})]
    [:div.flex-row
     [:input#kulu-normaali.vayla-radio
      (merge {:id id
              :type :radio
              :name ryhma
              :disabled disabloitu?
              :on-change muutos-fn}
             checked)]
     [:label {:for id} teksti]]))

(defmethod tee-kentta :radio-group [{:keys [vaihtoehdot vaihtoehto-nayta vaihtoehto-arvo nayta-rivina?
                                            oletusarvo vayla-tyyli? disabloitu?]} data]
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
     (let [group-id (gensym (str "radio-group-"))
           radiobuttonit (doall
                           (for [vaihtoehto vaihtoehdot
                                 :let [vaihtoehdon-arvo (if vaihtoehto-arvo
                                                          (vaihtoehto-arvo vaihtoehto)
                                                          vaihtoehto)]]
                             (if vayla-tyyli?
                               ^{:key (str "radio-group-" (vaihtoehto-nayta vaihtoehto))}
                               [vayla-radio {:teksti    (vaihtoehto-nayta vaihtoehto)
                                             :muutos-fn #(let [valittu? (-> % .-target .-checked)]
                                                           (when valittu?
                                                             (reset! data vaihtoehdon-arvo)))
                                             :disabloitu? disabloitu?
                                             :valittu?  (or (and (nil? valittu) (= vaihtoehto oletusarvo))
                                                            (= valittu vaihtoehdon-arvo))
                                             :ryhma     group-id
                                             :id        (gensym (str "radio-group-" (vaihtoehto-nayta vaihtoehto)))}]
                               ^{:key (str "radio-group-" (vaihtoehto-nayta vaihtoehto))}
                               [:div.radio
                                [:label
                                 [:input {:type "radio"
                                          ;; Samoin asetetaan checkbox valituksi luontivaiheessa,
                                          ;; jos parametri annettu
                                          :checked (or (and (nil? valittu) (= vaihtoehto oletusarvo))
                                                       (= valittu vaihtoehto))
                                          :on-change #(let [valittu? (-> % .-target .-checked)]
                                                        (when valittu?
                                                          (reset! data vaihtoehdon-arvo)))}]
                                 (vaihtoehto-nayta vaihtoehto)]])))]
       (if nayta-rivina?
         [:div {:style {:display "flex" :flex-direction "row" :flex-wrap "wrap"
                        :justify-content "flex-start"}}
          (map-indexed (fn [i cb]
                         ^{:key (str "radio-button-" i)}
                         [:div {:style {:flex-grow 1}} cb])
                       radiobuttonit)]
         radiobuttonit))]))

(defmethod tee-kentta :valinta
  ([{:keys [alasveto-luokka valinta-nayta valinta-arvo tasaa linkki-fn linkki-icon
            valinnat valinnat-fn rivi on-focus on-blur jos-tyhja
            jos-tyhja-fn disabled? fokus-klikin-jalkeen? virhe?
            nayta-ryhmat ryhmittely ryhman-otsikko vayla-tyyli? elementin-id
            pakollinen?]} data]
    ;; valinta-arvo: funktio rivi -> arvo, jolla itse lomakken data voi olla muuta kuin valinnan koko item
    ;; esim. :id
    (assert (or valinnat valinnat-fn) "Anna joko valinnat tai valinnat-fn")

    (let [nykyinen-arvo @data
          valinnat (or valinnat (valinnat-fn rivi))
          opts {:class                 (y/luokat "alasveto-gridin-kentta" alasveto-luokka (y/tasaus-luokka tasaa))
                :valinta               (if valinta-arvo
                                         (some #(when (= (valinta-arvo %) nykyinen-arvo) %) valinnat)
                                         nykyinen-arvo)
                :valitse-fn            #(reset! data
                                                (if valinta-arvo
                                                  (valinta-arvo %)
                                                  %))
                :fokus-klikin-jalkeen? fokus-klikin-jalkeen?
                :nayta-ryhmat          nayta-ryhmat
                :ryhmittely            ryhmittely
                :ryhman-otsikko        ryhman-otsikko
                :virhe?                virhe?
                :on-focus              on-focus
                :on-blur               on-blur
                :format-fn             (if (empty? valinnat)
                                         (or jos-tyhja-fn (constantly (or jos-tyhja "Ei valintoja")))
                                         (or (and valinta-nayta #(valinta-nayta % true)) str))
                :disabled              disabled?
                :pakollinen?           pakollinen?
                :vayla-tyyli?          vayla-tyyli?
                :elementin-id elementin-id}]
      (if-not (and linkki-fn nykyinen-arvo linkki-icon)
        [livi-pudotusvalikko opts
         valinnat]
        [:div.valinta-ja-linkki-container
         [:span {:style {:color "#004D99"}}
          [napit/nappi ""
           #(linkki-fn nykyinen-arvo)
           {:ikoni linkki-icon
            :ikoninappi? true
            :luokka "valinnan-vierusnappi napiton-nappi"}]]
         [livi-pudotusvalikko opts
          valinnat]])))
  ([{:keys [jos-tyhja]} data data-muokkaus-fn]
   ;; HUOM!! Erona 2-arity tapaukseen, valinta-nayta funktiolle annetaan vain yksi argumentti kahden sijasta
    (let [jos-tyhja-default-fn (constantly (or jos-tyhja "Ei valintoja"))]
      (fn [{:keys [alasveto-luokka valinta-nayta valinta-arvo data-cy
                   valinnat valinnat-fn rivi on-focus on-blur jos-tyhja
                   jos-tyhja-fn disabled? fokus-klikin-jalkeen? virhe?
                   nayta-ryhmat ryhmittely ryhman-otsikko vayla-tyyli? elementin-id]} data data-muokkaus-fn]
        (assert (not (satisfies? IDeref data)) "Jos käytät tee-kentta 3 aritylla, data ei saa olla derefable. Tämä sen takia, ettei React turhaan renderöi elementtiä")
        (assert (fn? data-muokkaus-fn) "Data-muokkaus-fn pitäisi olla funktio, joka muuttaa näytettävää dataa jotenkin")
        (assert (or valinnat valinnat-fn) "Anna joko valinnat tai valinnat-fn")
        (let [valinnat (or valinnat (valinnat-fn rivi))]
          [livi-pudotusvalikko {:class                 (str "alasveto-gridin-kentta " alasveto-luokka)
                                :valinta               (if valinta-arvo
                                                         (some #(when (= (valinta-arvo %) data) %) valinnat)
                                                         data)
                                :valitse-fn            data-muokkaus-fn
                                :fokus-klikin-jalkeen? fokus-klikin-jalkeen?
                                :nayta-ryhmat          nayta-ryhmat
                                :ryhmittely            ryhmittely
                                :ryhman-otsikko        ryhman-otsikko
                                :on-focus              on-focus
                                :on-blur               on-blur
                                :virhe?                virhe?
                                :format-fn             (if (empty? valinnat)
                                                         (or jos-tyhja-fn jos-tyhja-default-fn)
                                                         (or valinta-nayta str))
                                :disabled              disabled?
                                :data-cy               data-cy
                                :vayla-tyyli?          vayla-tyyli?
                                :elementin-id elementin-id}
           valinnat])))))

(defn- nayta-arvo-valinta-tai-radio-group
  [{:keys [valinta-nayta valinta-arvo
           valinnat valinnat-fn rivi hae
           jos-tyhja-fn jos-tyhja]} data]
  (let [nykyinen-arvo @data
        valinnat (or valinnat (valinnat-fn rivi))
        valinta (if valinta-arvo
                  (some #(when (= (valinta-arvo %) nykyinen-arvo) %) valinnat)
                  nykyinen-arvo)]
    [:span (if (empty? valinnat)
             ((or jos-tyhja-fn (constantly (or jos-tyhja "Ei valintoja"))) valinta)
             (or ((or valinta-nayta str false) valinta) valinta))]))

(defmethod nayta-arvo :valinta [opts data]
  [nayta-arvo-valinta-tai-radio-group opts data])

(defmethod nayta-arvo :radio-group
  [opts data]
  (let [opts (clojure.set/rename-keys opts {:vaihtoehto-arvo :valinta-arvo
                                            :vaihtoehto-nayta :valinta-nayta
                                            :vaihtoehdot :valinnat
                                            :vaihtoehdot-fn :valinnat-fn})]
    [nayta-arvo-valinta-tai-radio-group opts data]))

(defmethod tee-kentta :kombo [{:keys [valinnat on-focus on-blur lomake? disabled?]} data]
  (let [auki (atom false)]
    (fn [{:keys [valinnat]} data]
      (let [nykyinen-arvo (or @data "")]
        [:div.dropdown {:class (when @auki "open")}
         [:input.kombo {:class     (cond-> nil
                                           lomake? (str "form-control ")
                                           disabled? (str "disabled"))
                        :type      "text" :value nykyinen-arvo
                        :on-focus  on-focus
                        :on-blur   on-blur
                        :disabled  disabled?
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
(defmethod tee-kentta :pvm [{:keys [pvm-tyhjana rivi on-focus lomake? pakota-suunta validointi on-datepicker-select vayla-tyyli?]} data]

  (let [;; pidetään kirjoituksen aikainen ei validi pvm tallessa
        p @data
        date->teksti #(if % (pvm/pvm %) "")
        teksti (atom (date->teksti p))
        ;; pidetään edellinen data arvo tallessa, jotta voidaan muuttaa teksti oikeaksi
        ;; jos annetun data-atomin arvo muuttuu muualla kuin tässä komponentissa
        vanha-data (cljs.core/atom {:data            p
                                    :muokattu-tassa? true})
        muuta-data! (fn [arvo]
                      (swap! vanha-data assoc :data arvo :muokattu-tassa? true)
                      (reset! data arvo)
                      (when on-datepicker-select
                        (on-datepicker-select arvo)))
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
                                      (muuta-data! d)))))
        muuta! (fn [data t]
                 (when (or (re-matches +pvm-regex+ t)
                           (str/blank? t))
                   (reset! teksti t))
                 (when (str/blank? t)
                   (muuta-data! nil)))]
    (komp/luo
      (komp/klikattu-ulkopuolelle #(reset! auki false))
      {:component-will-receive-props
       (fn [this _ {:keys [focus] :as s} data]
         (let [p @data]
           (reset! teksti (if p
                            (pvm/pvm p)
                            ""))))
       :reagent-render
       (fn [{:keys [on-focus on-blur placeholder rivi validointi on-datepicker-select virhe?]} data]
         (let [nykyinen-pvm @data
               {vanha-data-arvo :data muokattu-tassa? :muokattu-tassa?} @vanha-data
               _ (when (and (not= nykyinen-pvm vanha-data-arvo)
                            (not muokattu-tassa?))
                   (reset! teksti (date->teksti nykyinen-pvm)))
               nykyinen-teksti @teksti
               pvm-tyhjana (or pvm-tyhjana (constantly nil))
               validoi? (some? validointi)
               validoi (r/partial validoi-fn validoi? validointi)
               naytettava-pvm (or
                                (pvm/->pvm nykyinen-teksti)
                                nykyinen-pvm
                                (pvm-tyhjana rivi))]
           (swap! vanha-data assoc :data nykyinen-pvm :muokattu-tassa? false)
           [:span.pvm-kentta
            {:on-click #(do (reset! auki true) nil)
             :style    {:display "inline-block"}}
            [:input.pvm {:class       (cond
                                        vayla-tyyli? (str "input-" (if virhe? "error-" "") "default komponentin-input")
                                        lomake? "form-control")
                         :placeholder (or placeholder "pp.kk.vvvv")
                         :value       nykyinen-teksti
                         :on-focus    #(do (when on-focus (on-focus)) (reset! auki true) %)
                         :on-change   #(muuta! data (-> % .-target .-value))
                         ;; keycode 9 = Tab. Suljetaan datepicker kun painetaan tabia.
                         :on-key-down #(when (or (= key-code-tab (-> % .-keyCode)) (= key-code-tab (-> % .-which))
                                                 (= key-code-enter (-> % .-keyCode)) (= key-code-enter (-> % .-which)))
                                         (teksti-paivamaaraksi! validoi data nykyinen-teksti)
                                         (reset! auki false)
                                         true)
                         :on-blur     #(let [arvo (.. % -target -value)
                                             pvm (pvm/->pvm arvo)]
                                         (when on-blur
                                           (on-blur %))
                                         (if (and pvm (not (validoi pvm)))
                                           (do (muuta-data! nil)
                                               (reset! teksti ""))
                                           (teksti-paivamaaraksi! validoi data arvo)))}]
            (when @auki
              [pvm-valinta/pvm-valintakalenteri {:valitse       #(when (validoi %)
                                                                   (reset! auki false)
                                                                   (muuta-data! %)
                                                                   (reset! teksti (pvm/pvm %)))
                                                 :pvm           naytettava-pvm
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
               [:input.pvm {:class       (when lomake? "form-control")
                            :placeholder "pp.kk.vvvv"
                            :on-click    #(do (.stopPropagation %)
                                              (.preventDefault %)
                                              (reset! auki true)
                                              %)
                            :value       nykyinen-pvm-teksti
                            :on-focus    #(do (when on-focus (on-focus)) (reset! auki true) %)
                            :on-change   #(muuta-pvm! (-> % .-target .-value))
                            ;; keycode 9 = Tab. Suljetaan datepicker kun painetaan tabia.
                            :on-key-down #(when (or (= 9 (-> % .-keyCode)) (= 9 (-> % .-which)))
                                            (reset! auki false)
                                            %)
                            :on-blur     #(do (when on-blur (on-blur %)) (koske-pvm!) (aseta! false) %)}]
               (when @auki
                 [pvm-valinta/pvm-valintakalenteri {:valitse       #(do (reset! auki false)
                                                                        (muuta-pvm! (pvm/pvm %))
                                                                        (koske-pvm!)
                                                                        (aseta! true))
                                                    :pvm           naytettava-pvm
                                                    :pakota-suunta pakota-suunta}])]
              [:td
               [:input {:class       (str (when lomake? "form-control")
                                          (when (and (not (re-matches +validi-aika-regex+
                                                                      nykyinen-aika-teksti))
                                                     (pvm/->pvm nykyinen-pvm-teksti))
                                            " puuttuva-arvo"))
                        :placeholder "tt:mm"
                        :size        5 :max-length 5
                        :value       nykyinen-aika-teksti
                        :on-change   #(muuta-aika! (-> % .-target .-value))
                        :on-blur     #(do (koske-aika!) (aseta! false))}]]]]]])))))

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
  ([lomake? muuta! blur placeholder value key disabled? vayla-tyyli?] (tr-kentan-elementti lomake? muuta! blur placeholder value key disabled? vayla-tyyli? (str "tr-" (name key))))
  ([lomake? muuta! blur placeholder value key disabled? vayla-tyyli? luokat]
   [:input.tierekisteri {:class (str
                                  luokat " " "tr-" (name key) " "
                                  (when (and lomake? (not vayla-tyyli?)) "form-control ")
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

(defn- tierekisterikentat-table [{:keys [pakollinen? disabled?]} tie aosa aet losa loppuet tr-otsikot? sijainnin-tyhjennys karttavalinta virhe
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

(defn- tierekisterikentat-flex [{:keys [pakollinen? disabled?]} tie aosa aet losa loppuet tr-otsikot? sijainnin-tyhjennys karttavalinta virhe
                                piste? vaadi-vali?]
  [:div {:style {:max-width "340px"}}
   [:div {:style {:display "flex"}}
    [:div
     [:label.control-label
      [:span
       [:span.kentan-label "Tie"]
       (when pakollinen? [:span.required-tahti " *"])]]
     tie [:span]]
    [:div
     [:label.control-label
      [:span
       [:span.kentan-label "aosa"]
       (when pakollinen? [:span.required-tahti " *"])]]
     aosa [:span]]
    [:div
     [:label.control-label
      [:span
       [:span.kentan-label "aet"]
       (when pakollinen? [:span.required-tahti " *"])]]
     aet [:span]]
    (when (not piste?)
      [:div
       [:label.control-label
        [:span
         [:span.kentan-label "losa"]
         (when pakollinen? [:span.required-tahti " *"])]]
       losa [:span]])
    (when (not piste?)
      [:div
       [:label.control-label
        [:span
         [:span.kentan-label "let"]
         (when pakollinen? [:span.required-tahti " *"])]]
       loppuet [:span]])

    (when virhe
      [:div virhe])]
   [:div {:style {:display "flex" :flex-direction "column"}}
    [:div.karttavalinta
     karttavalinta]]])


(defn- tierekisterikentat-rivitetty
  "Erilainen tyyli TR valitsimelle, jos lomake on hyvin kapea.
  Rivittää tierekisterivalinnan usealle riville."
  [{:keys [pakollinen? disabled?]} tie aosa aet losa loppuet tr-otsikot? sijainnin-tyhjennys karttavalinta virhe]
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
                                                   avaimet voi-valita-kartalta? vayla-tyyli?]} data]
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
                    (loki/log "Lopetetaan TR sijaintipäivitys")
                    (async/close! tr-osoite-ch)
                    (when voi-valita-kartalta?
                      (reset! kartta/pida-geometriat-nakyvilla? kartta/pida-geometria-nakyvilla-oletusarvo)
                      (tasot/poista-geometria! :tr-valittu-osoite)
                      (kartta/zoomaa-geometrioihin))))

      (fn [{:keys [tyyli lomake? sijainti piste? vaadi-vali? tr-otsikot? vayla-tyyli? disabled?]} data]
        (let [avaimet (or avaimet tr-osoite-raaka-avaimet)
              _ (assert (= 5 (count avaimet))
                        (str "TR-osoitekenttä tarvii 5 avainta (tie,aosa,aet,losa,let), saatiin: "
                             (count avaimet)))
              tr-otsikot? (if (nil? tr-otsikot?)
                            true
                            tr-otsikot?)
              [numero-avain alkuosa-avain alkuetaisyys-avain loppuosa-avain loppuetaisyys-avain]
              avaimet

              tierekisterikentat (cond
                                   (and (not vayla-tyyli?) (= tyyli :rivitetty)) tierekisterikentat-rivitetty
                                   vayla-tyyli? tierekisterikentat-flex
                                   :default tierekisterikentat-table)

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
                           {numero-avain        numero
                            alkuosa-avain       alkuosa
                            alkuetaisyys-avain  alkuetaisyys
                            loppuosa-avain      loppuosa
                            loppuetaisyys-avain loppuetaisyys})
              luokat (if vayla-tyyli? "input-default" "")]
          ;(loki/log "sijainti >" @sijainti avaimet numero alkuosa numero-avain alkuosa-avain)
          [:span {:class (str "tierekisteriosoite-kentta "
                              (when @virheet " sisaltaa-virheen")
                              (when vayla-tyyli? " vayla"))}
           (when (and @virheet (false? ala-nayta-virhetta-komponentissa?))
             [:div {:class "virheet"}
              [:div {:class "virhe"}
               [:span (ikonit/livicon-warning-sign) [:span @virheet]]]])

           (let [optiot {:pakollinen pakollinen?}]
             [tierekisterikentat
              optiot
              [tr-kentan-elementti lomake? muuta! blur
               "Tie" numero numero-avain (or disabled?
                                             @karttavalinta-kaynnissa?) vayla-tyyli? luokat]
              [tr-kentan-elementti lomake? muuta! blur
               "aosa" alkuosa alkuosa-avain (or disabled?
                                                @karttavalinta-kaynnissa?) vayla-tyyli? luokat]
              [tr-kentan-elementti lomake? muuta! blur
               "aet" alkuetaisyys alkuetaisyys-avain (or disabled?
                                                         @karttavalinta-kaynnissa?) vayla-tyyli? luokat]
              [tr-kentan-elementti lomake? muuta! blur
               "losa" loppuosa loppuosa-avain (or disabled?
                                                  @karttavalinta-kaynnissa?) vayla-tyyli? luokat]
              [tr-kentan-elementti lomake? muuta! blur
               "let" loppuetaisyys loppuetaisyys-avain (or disabled?
                                                           @karttavalinta-kaynnissa?) vayla-tyyli? luokat]
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
                   {:ikoni (ikonit/map-marker)
                    :disabled disabled?}]
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
              vaadi-vali?])])))))

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
              {:disabled             (or @paikannus-kaynnissa? @karttavalinta-kaynnissa? disabled?)
               :ikoni                (ikonit/screenshot)
               :tallennus-kaynnissa? @paikannus-kaynnissa?}])

           (when karttavalinta?
             (if-not @karttavalinta-kaynnissa?
               [napit/yleinen-ensisijainen
                "Valitse kartalta"
                #(when-not @karttavalinta-kaynnissa?
                   (aloita-karttavalinta))
                {:disabled (or @paikannus-kaynnissa? @karttavalinta-kaynnissa? disabled?)
                 :ikoni    (ikonit/map-marker)}]
               [sijaintivalitsin/sijaintivalitsin {:kun-peruttu #(lopeta-karttavalinta)
                                                   :kun-valmis  #(do
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
      (pvm/map->Aika {:tunnit   (js/parseInt t)
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
    [:input {:class       (str (when lomake? "form-control")
                               (when-not (:tunnit @data) " puuttuva-arvo"))
             :placeholder placeholder
             :on-change   (fn [e]
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
             :on-focus    on-focus
             :on-blur     #(do
                             (when on-blur
                               (on-blur %))
                             (when-let [t (:keskenerainen @data)]
                               (when (and (re-matches #"\d+" t)
                                          (<= 0 (js/parseInt t) 23))
                                 (reset! data (pvm/->Aika (js/parseInt t) 0 nil)))))
             :value       (or keskenerainen (fmt/aika aika))}]))

(defmethod tee-kentta :toggle [{:keys [paalle-teksti pois-teksti toggle!]} data]
  (assert (and paalle-teksti pois-teksti)
          "Määrittele :paalle-teksti ja :pois-teksti kentät!")
  (let [arvo-nyt @data]
    [napit/yleinen-toissijainen (if arvo-nyt
                                  pois-teksti
                                  paalle-teksti)
     (or toggle! #(swap! data not))
     {:luokka "btn-xs"}]))

(defmethod tee-kentta :valiotsikko [{:keys [teksti]} data]
  [:div [:h3 teksti]])

(defn vayla-lomakekentta
  "Väylä-tyylinen tekstikenttä"
  [otsikko & params]
  (let [avaimet->proppi {:arvo :value}
        id (gensym "kulukentta-")
        propit (into {:type :text
                      :for  id}
                     (map (fn [[avain arvo]]
                            [(if (contains? avaimet->proppi avain)
                               (avain avaimet->proppi)
                               avain)
                             arvo]))
                     (if (and (= 1 (count params))
                              (map? (first params)))
                       (first params)
                       (partition 2 params)))
        {komponentti            :komponentti
         komponentin-argumentit :komponentin-argumentit
         tyylit                 :tyylit
         otsikko-tag            :otsikko-tag
         ikoni                  :ikoni
         virhe-viesti           :virhe} propit
        propit (apply dissoc propit #{:komponentti :tyylit :komponentin-argumentit :otsikko-tag :ikoni :virhe})]
    [:div {:class (or (:kontti tyylit)
                      #{"kulukentta"})}
     [(if-not (nil? otsikko-tag)
        otsikko-tag
        :label) {:id    id
                 :class (or (:otsikko tyylit)
                            #{})} otsikko]
     (if komponentti
       [komponentti (or komponentin-argumentit {})]
       [:<>
        [:div.ikoni-sisaan
         (when-not (nil? ikoni) [ikoni])
         [:input.input-default.komponentin-input propit]]
        (when virhe-viesti
          [:span.virhe virhe-viesti])])]))

(defn aikavali
  "Aikavälin valinta -komponentti

  Parametrit
  valinta-fn - kutsutaan valinnan yhteydessä, saa kaksi parametria. Joko :alkupvm tai :loppupvm ensimmäisenä indikoimaan
  mistä valitsimesta kutsuttu, sitten arvon.  Pakollinen
  :sumeutus-kun-molemmat-fn - kutsuu kun molemmat kentät on valittu on-blur yhteydessä. saa alkupvm ja loppupvm parametreina
  :pvm-alku / :pvm-loppu - komponentille voi antaa omat pvm-alku / loppu -arvot näillä. Mikäli näitä ei anneta, ovat molemmat
  arvot komponentin sisäisessä tilassa.
  :ikoni - tällä voi määrittää ikonin, mitä käytetään
  :rajauksen-alkupvm / -loppupvm antavat maksimi raja-arvot päivämäärille
  "
  [{:keys [ikoni rajauksen-alkupvm rajauksen-loppupvm] :as _optiot}]
  (let [auki? (r/atom false)
        fokus-vaerit {:outline-color  "#0068B3"
                      :outline-width  "3px"
                      :outline-offset "-3px"}
        sisaiset (r/atom {:syottobufferi {:alku  ""
                                          :loppu ""}
                          :koskettu?     {:alku  false
                                          :loppu false}
                          :valittu-pvm   :alkupvm})
        sumeutus-fn (fn [{:keys [valinta-fn syottobufferi alku? arvot koskettu? sumeutus-kun-molemmat-fn]} _]
                      (let [alku-tai-loppu-avain (if alku? :alku :loppu)
                            pvm-bufferista (pvm/->pvm syottobufferi)
                            vasta-arvo (get arvot
                                            (alku-tai-loppu-avain {:alku  :loppu
                                                                   :loppu :alku}))
                            alkuarvo (cond
                                       (and
                                         (not alku?)
                                         (nil? vasta-arvo)) rajauksen-alkupvm
                                       (not alku?) vasta-arvo
                                       (string/blank? pvm-bufferista) rajauksen-alkupvm
                                       :else pvm-bufferista)
                            loppuarvo (cond
                                        (and
                                          alku?
                                          (nil? vasta-arvo)) rajauksen-loppupvm
                                        alku? vasta-arvo
                                        (string/blank? pvm-bufferista) rajauksen-loppupvm
                                        :else pvm-bufferista)]
                        (swap! sisaiset (fn [tila]
                                          (-> tila
                                              (assoc-in [:syottobufferi alku-tai-loppu-avain] "")
                                              (assoc-in [:koskettu? alku-tai-loppu-avain] false))))
                        (when (and alkuarvo loppuarvo)
                          (sumeutus-kun-molemmat-fn alkuarvo loppuarvo))
                        (valinta-fn (alku-tai-loppu-avain {:alku :alkupvm :loppu :loppupvm}) pvm-bufferista)))
        pvm-valintakentta (fn [{:keys [valinta-fn otsikko syottobufferi koskettu? valittu? pvm-arvo kentan-tyyppi
                                       sumeutus-kun-molemmat-fn arvot]}]
                            (let [alku? (case kentan-tyyppi
                                          :alkupvm true
                                          :loppupvm false)
                                  polku (if alku?
                                          :alku
                                          :loppu)]
                              [vayla-lomakekentta
                               otsikko
                               :tyylit {:kontti #{}}
                               :style (if valittu?
                                        fokus-vaerit
                                        {})
                               :ikoni ikoni
                               :arvo (cond koskettu? syottobufferi
                                           pvm-arvo (pvm/pvm pvm-arvo)
                                           :else "")
                               :on-focus #(swap! sisaiset (fn [tila]
                                                            (cond-> tila
                                                                    true (assoc :valittu-pvm kentan-tyyppi)
                                                                    (not (nil? pvm-arvo)) (assoc-in [:syottobufferi polku] (pvm/pvm pvm-arvo)))))
                               :on-blur (r/partial sumeutus-fn {:valinta-fn               valinta-fn
                                                                :syottobufferi            syottobufferi
                                                                :alku?                    alku?
                                                                :koskettu?                koskettu?
                                                                :arvot                    arvot
                                                                :sumeutus-kun-molemmat-fn sumeutus-kun-molemmat-fn})
                               :on-key-down #(when (and (pvm-valinta/pvm-popupin-sulkevat-nappaimet (.-keyCode %))
                                                        (or (not (string/blank? syottobufferi))
                                                            koskettu?))
                                               (valinta-fn kentan-tyyppi (pvm/->pvm syottobufferi)))
                               :on-change #(swap! sisaiset
                                                  (fn [tila]
                                                    (-> tila
                                                        (assoc-in [:syottobufferi polku] (.. % -target -value))
                                                        (assoc-in [:koskettu? polku] true))))]))]
    (komp/luo
      (komp/klikattu-ulkopuolelle #(reset! auki? false))
      (fn [{:keys [valinta-fn pvm-alku pvm-loppu disabled sumeutus-kun-molemmat-fn] :as _optiot}]
        (let [{:keys [valittu-pvm syottobufferi koskettu?]} @sisaiset]
          [:div.aikavali
           [vayla-lomakekentta
            "Aikaväli"
            :ikoni ikoni
            :placeholder "-valitse-"
            :value (or (when (and pvm-alku pvm-loppu)
                         (str (pvm/pvm pvm-alku) "-" (pvm/pvm pvm-loppu)))
                       "")
            :on-click #(swap! auki? not)
            :read-only true]
           (when @auki?
             [:div.aikavali-dropdown
              [pvm-valintakentta {:valinta-fn               valinta-fn
                                  :otsikko                  "Alkupvm"
                                  :syottobufferi            (:alku syottobufferi)
                                  :kentan-tyyppi            :alkupvm
                                  :koskettu?                (:alku koskettu?)
                                  :valittu?                 (= :alkupvm valittu-pvm)
                                  :pvm-arvo                 pvm-alku
                                  :sumeutus-kun-molemmat-fn sumeutus-kun-molemmat-fn
                                  :arvot                    {:alku  pvm-alku
                                                             :loppu pvm-loppu}}]
              [pvm-valintakentta {:valinta-fn               valinta-fn
                                  :otsikko                  "Loppupvm"
                                  :syottobufferi            (:loppu syottobufferi)
                                  :kentan-tyyppi            :loppupvm
                                  :koskettu?                (:loppu koskettu?)
                                  :valittu?                 (= :loppupvm valittu-pvm)
                                  :pvm-arvo                 pvm-loppu
                                  :sumeutus-kun-molemmat-fn sumeutus-kun-molemmat-fn
                                  :arvot                    {:alku  pvm-alku
                                                             :loppu pvm-loppu}}]
              [:label (str "Klikkaa kalenterista " (case valittu-pvm
                                                     :alkupvm "rajauksen alku"
                                                     :loppupvm "rajauksen loppu"))
               [pvm-valinta/pvm-valintakalenteri {:vayla-tyyli?  false
                                                  :flowissa?     true
                                                  :valitse       #(let [{:keys [alkupvm loppupvm]} (assoc {:alkupvm  pvm-alku
                                                                                                           :loppupvm pvm-loppu}
                                                                                                     valittu-pvm
                                                                                                     %)]
                                                                    (swap! sisaiset assoc :valittu-pvm (case valittu-pvm
                                                                                                         :loppupvm :alkupvm
                                                                                                         :alkupvm :loppupvm))
                                                                    (valinta-fn valittu-pvm %)
                                                                    (when (and alkupvm loppupvm)
                                                                      (sumeutus-kun-molemmat-fn alkupvm loppupvm)))
                                                  :valittava?-fn #(and (pvm/valissa? %
                                                                                     rajauksen-alkupvm
                                                                                     rajauksen-loppupvm)
                                                                       (case valittu-pvm
                                                                         :loppupvm (pvm/jalkeen? %
                                                                                                 (or pvm-alku
                                                                                                     rajauksen-alkupvm))
                                                                         :alkupvm (pvm/ennen? %
                                                                                              (or pvm-loppu
                                                                                                  rajauksen-loppupvm))))
                                                  :pvm           (case valittu-pvm
                                                                   :alkupvm pvm-alku
                                                                   :loppupvm pvm-loppu)}]]])])))))
