(ns harja.ui.kentat
  "UI input kenttien muodostaminen typpin perusteella, esim. grid ja lomake komponentteihin."
  (:require [reagent.core :refer [atom] :as r]
            [harja.pvm :as pvm]
            [harja.ui.pvm :as pvm-valinta]
            [harja.ui.yleiset :refer [livi-pudotusvalikko linkki ajax-loader nuolivalinta] :as yleiset]
            [harja.ui.protokollat :refer [hae]]
            [harja.ui.komponentti :as komp]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.tierekisteri :as tr]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.tiedot.navigaatio :as nav]
            [clojure.string :as str]
            [goog.string :as gstr]
            [cljs.core.async :refer [<! >! chan] :as async]

            [harja.views.kartta :as kartta]
            [harja.geo :as geo]

            ;; Tierekisteriosoitteen muuntaminen sijainniksi tarvii tämän
            [harja.tyokalut.vkm :as vkm]
            [harja.atom :refer [paivittaja]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; PENDING: dokumentoi rajapinta, mitä eri avaimia kentälle voi antaa

;; r/wrap skeeman arvolle
(defn atomina [{:keys [nimi hae aseta]} data vaihda!]
  (let [hae (or hae #(get % nimi))]
    (r/wrap (hae data)
            (fn [uusi]
              ;; Resetoi data, jos uusi data annettu
              (when (not= uusi (nimi data))
                (if aseta
                  (vaihda! (aseta data uusi))
                  (vaihda! (assoc data nimi uusi))))))))

(defn vain-luku-atomina [arvo]
  (r/wrap arvo
          #(assert false (str "Ei voi kirjoittaa vain luku atomia arvolle: " (pr-str arvo)))))


(defmulti tee-kentta
          "Tekee muokattavan kentän tyypin perusteella"
          (fn [t _] (:tyyppi t)))

(defmulti nayta-arvo
          "Tekee vain-luku näyttömuodon kentän arvosta tyypin perusteella. Tämän tarkoituksena ei ole tuottaa 'disabled' tai 'read-only' elementtejä vaan tekstimuotoinen kuvaus arvosta. Oletustoteutus muuntaa datan vain merkkijonoksi."
          (fn [t _] (:tyyppi t)))


(defmethod nayta-arvo :default [_ data]
  [:span (str @data)])

(defmethod tee-kentta :haku [{:keys [lahde nayta placeholder pituus lomake?]} data]
  (let [nyt-valittu @data
        teksti (atom (if nyt-valittu
                       ((or nayta str) nyt-valittu) ""))
        tulokset (atom nil)
        valittu-idx (atom nil)]
    (fn [_ data]
      [:div.dropdown {:class (when-not (nil? @tulokset) "open")}

       [:input {:class       (when lomake? "form-control")
                :value       @teksti
                :placeholder placeholder
                :size        pituus
                :on-change   #(when (= (.-activeElement js/document) (.-target %))
                               ;; tehdään haku vain jos elementti on fokusoitu
                               ;; IE triggeröi on-change myös ohjelmallisista muutoksista
                               (let [v (-> % .-target .-value)]
                                 (reset! data nil)
                                 (reset! teksti v)
                                 (if (> (count v) 2)
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
                                                 (reset! tulokset nil)))))}]
       [:ul.dropdown-menu {:role "menu"}
        (let [nykyiset-tulokset @tulokset
              idx @valittu-idx]
          (if (= :haetaan nykyiset-tulokset)
            [:li {:role "presentation"} (ajax-loader) " haetaan: " @teksti]
            (if (empty? nykyiset-tulokset)
              [:span.ei-hakutuloksia "Ei tuloksia"]
              (doall (map-indexed (fn [i t]
                                    ^{:key (hash t)}
                                    [:li {:class (when (= i idx) "korostettu") :role "presentation"}
                                     [linkki ((or nayta str) t) #(do (reset! data t)
                                                                     (reset! teksti ((or nayta str) t))
                                                                     (reset! tulokset nil))]])
                                  nykyiset-tulokset)))))]])))



(defmethod tee-kentta :string [{:keys [nimi pituus-max pituus-min regex on-focus lomake? placeholder]} data]
  [:input {:class       (when lomake? "form-control")
           :placeholder placeholder
           :on-change   #(reset! data (-> % .-target .-value))
           :on-focus    on-focus
           :value       @data
           :max-length  pituus-max}])


;; Pitkä tekstikenttä käytettäväksi lomakkeissa, ei sovellu hyvin gridiin
;; pituus-max oletusarvo on 256, koska se on toteuman lisätiedon tietokantasarakkeissa
(defmethod tee-kentta :text [{:keys [placeholder nimi koko on-focus lomake? pituus-max]} data]
  (let [[koko-sarakkeet koko-rivit] koko
        rivit (atom (if (= :auto koko-rivit)
                      2
                      koko-rivit))
        pituus-max (or pituus-max 256)
        muuta! (fn [data e]
                 ;; alla pientä workaroundia koska selaimen max-length -ominaisuus ei tue rivinvaihtoja
                 (let [teksti (-> e .-target .-value)]
                   ;; jos copy-paste ylittäisi max-pituuden, eipä sallita sitä
                   (if (< (count teksti) pituus-max)
                     (reset! data teksti)
                     (reset! data (subs teksti 0 pituus-max)))))]
    (komp/luo
      (when (= koko-rivit :auto)
        {:component-did-update
         (fn [this _]
           (let [n (-> this r/dom-node
                       (.getElementsByTagName "textarea")
                       (aget 0))]
             (let [erotus (- (.-scrollHeight n) (.-clientHeight n))]
               (when (> erotus 0)
                 (swap! rivit + (/ erotus 19))))))})

      (fn [{:keys [nimi koko on-focus lomake?]} data]
        [:span.kentta-text
         [:textarea {:value       @data
                     :on-change   #(muuta! data %)
                     :on-focus    on-focus
                     :cols        (or koko-sarakkeet 80)
                     :rows        @rivit
                     :class (when lomake? "form-control")
                     :placeholder placeholder}]
         ;; näytetään laskuri kun merkkejä on jäljellä alle 25%
         (when (> (/ (count @data) pituus-max) 0.75)
           [:div (- pituus-max (count @data)) " merkkiä jäljellä"])]))))

(defmethod tee-kentta :numero [kentta data]
  (let [teksti (atom (str @data))]
    (r/create-class
      {:component-will-receive-props
       (fn [_ [_ _ data]]
         (swap! teksti
                (fn [olemassaoleva-teksti]
                  ;; Jos vanha teksti on sama kuin uusi, mutta perässä on "." tai "," ja mahdollisesti n kappaletta nollia,
                  ;; ei korvata.
                  ;; Tämä siksi että wraps käytössä props muuttuu joka renderillä ja keskeneräinen
                  ;; numeron syöttö (esim. "4,") ennen desimaalin kirjoittamista ylikirjoittuu
                  ;; Lisäksi esim. "4,0" parsitaan float-arvona kokonaisluvuksi 4, jolloin lukua "4,01" ei voi kirjoittaa.
                  (let [uusi (str @data)]
                    (if (or (and (gstr/startsWith olemassaoleva-teksti uusi)
                                 (re-matches #"(.|,)0*" (.substring olemassaoleva-teksti (count uusi))))
                            (when-not (:vaadi-ei-negatiivinen? kentta)
                              (= olemassaoleva-teksti (str "-" uusi))))
                      olemassaoleva-teksti
                      uusi)))))

       :reagent-render
       (fn [{:keys [lomake? kokonaisluku?] :as kentta} data]
         (let [nykyinen-teksti @teksti
               vaadi-ei-negatiivinen? (= :positiivinen-numero (:tyyppi kentta))
               kokonaisluku-re-pattern #"-?\d{1,10}"
               desimaaliluku-re-pattern (re-pattern (str "-?\\d{1,10}((\\.|,)\\d{0,"
                                                         (or (:desimaalien-maara kentta) 2)
                                                         "})?"))]
           [:input {:class       (when lomake? "form-control")
                    :type        "text"
                    :placeholder (:placeholder kentta)
                    :on-focus    (:on-focus kentta)
                    :on-blur     #(reset! teksti (str @data))
                    :value       nykyinen-teksti
                    :on-change   #(let [v (-> % .-target .-value)]
                                   (when (or (= v "")
                                             (when-not vaadi-ei-negatiivinen? (= v "-"))
                                             (re-matches (if kokonaisluku?
                                                           kokonaisluku-re-pattern
                                                           desimaaliluku-re-pattern) v))
                                     (reset! teksti v)

                                     (let [numero (if kokonaisluku?
                                                    (js/parseInt v)
                                                    (js/parseFloat (str/replace v #"," ".")))]
                                       (reset! data
                                               (when (not (js/isNaN numero))
                                                 numero)))))}]))})))

(defmethod tee-kentta :positiivinen-numero [kentta data]
  (tee-kentta (assoc kentta :vaadi-ei-negatiivinen? true
                            :tyyppi :numero) data))

(defmethod tee-kentta :email [{:keys [on-focus lomake?] :as kentta} data]
  [:input {:class     (when lomake? "form-control")
           :type      "email"
           :value     @data
           :on-focus  on-focus
           :on-change #(reset! data (-> % .-target .-value))}])



(defmethod tee-kentta :puhelin [{:keys [on-focus pituus lomake?] :as kentta} data]
  [:input {:class      (when lomake? "form-control")
           :type       "tel"
           :value      @data
           :max-length pituus
           :on-focus   on-focus
           :on-change  #(let [uusi (-> % .-target .-value)]
                         (when (re-matches #"(\s|\d)*" uusi)
                           (reset! data uusi)))}])


(defmethod tee-kentta :radio [{:keys [valinta-nayta valinta-arvo valinnat on-focus]} data]
  (let [arvo (or valinta-arvo identity)
        nayta (or valinta-nayta str)
        nykyinen-arvo @data]
    [:span.radiovalinnat
     (doall
       (map-indexed (fn [i valinta]
                      (let [otsikko (nayta valinta)
                            arvo (arvo valinta)]
                        ^{:key otsikko}
                        [:span.radiovalinta
                         [:input {:type      "radio" :value i
                                  :checked   (= nykyinen-arvo arvo)
                                  :on-change #(reset! data arvo)}]
                         [:span.radiovalinta-label.klikattava {:on-click #(reset! data arvo)}
                          otsikko]]))
                    valinnat))]))

(defmethod nayta-arvo :radio [{:keys [valinta-nayta]} data]
  [:span ((or valinta-nayta str) @data)])

(defmethod tee-kentta :boolean [{:keys [otsikko boolean-otsikko]} data]
  [:div.checkbox
   [:label
    [:input {:type      "checkbox" :checked @data
             :on-change #(do (reset! data (-> % .-target .-checked)) nil)}
     (or boolean-otsikko otsikko)]]])

(defmethod nayta-arvo :boolean [{:keys [otsikko]} data]
  [:span (if @data
           "\u2713 "
           "\u2610 ") otsikko])

(defmethod tee-kentta :boolean-group [{:keys [vaihtoehdot vaihtoehto-nayta valitse-kaikki? tyhjenna-kaikki?]} data]
  (let [vaihtoehto-nayta (or vaihtoehto-nayta
                             #(clojure.string/capitalize (name %)))
        valitut (set (or @data #{}))]
    [:span
     ;; Esimerkiksi historiakuvassa boolean-grouppia käytetään siten, että useampi boolean-group käyttää
     ;; samaa data-atomia säilyttämään valitut suodattimet. Siksi tyhjennyksessä ja kaikkien valitsemisessa
     ;; ei voi vain yksinkertaisesti resetoida datan sisältöä tyhjäksi tai kaikiksi vaihtoehdoiksi.
     (when tyhjenna-kaikki?
       [:button.nappi-toissijainen {:on-click #(reset! data (apply disj @data vaihtoehdot))}
        [:span.livicon-trash " Tyhjennä kaikki"]])
     (when valitse-kaikki?
       [:button.nappi-toissijainen {:on-click #(swap! data clojure.set/union (into #{} vaihtoehdot))}
        [:span.livicon-check " Valitse kaikki"]])
     (doall
      (for [v vaihtoehdot]
        ^{:key (str "boolean-group-" (name v))}
        [:div.checkbox
         [:label
          [:input {:type      "checkbox" :checked (if (valitut v) true false)
                   :on-change #(let [valittu? (-> % .-target .-checked)]
                                 (reset! data
                                         ((if valittu? conj disj) valitut v)))}
           (vaihtoehto-nayta v)]]]))]))

(defmethod tee-kentta :valinta [{:keys [alasveto-luokka valinta-nayta valinta-arvo
                                        valinnat valinnat-fn rivi on-focus jos-tyhja]} data]
  ;; valinta-arvo: funktio rivi -> arvo, jolla itse lomakken data voi olla muuta kuin valinnan koko item
  ;; esim. :id
  (assert (or valinnat valinnat-fn "Anna joko valinnat tai valinnat-fn"))
  (let [nykyinen-arvo @data
        valinnat (or valinnat (valinnat-fn rivi))]
    [livi-pudotusvalikko {:class      (str "alasveto-gridin-kentta " alasveto-luokka)
                          :valinta    (if valinta-arvo
                                        (some #(when (= (valinta-arvo %) nykyinen-arvo) %) valinnat)
                                        nykyinen-arvo)
                          :valitse-fn #(reset! data
                                               (if valinta-arvo
                                                 (valinta-arvo %)
                                                 %))
                          :on-focus   on-focus
                          :format-fn  (if (empty? valinnat)
                                        (constantly (or jos-tyhja "Ei valintoja"))
                                        (or valinta-nayta str))}
     valinnat]))

(defmethod nayta-arvo :valinta [{:keys [valinta-nayta valinta-arvo valinnat valinnat-fn rivi hae]} data]
  (let [nykyinen-arvo @data
        valinnat (or valinnat (valinnat-fn rivi))
        valinta (if valinta-arvo
                  (some #(when (= (valinta-arvo %) nykyinen-arvo) %) valinnat)
                  nykyinen-arvo)]
    [:span (or ((or valinta-nayta str) valinta) valinta)]))



(defmethod tee-kentta :kombo [{:keys [valinnat on-focus lomake?]} data]
  (let [auki (atom false)]
    (fn [{:keys [valinnat]} data]
      (let [nykyinen-arvo (or @data "")]
        [:div.dropdown {:class (when @auki "open")}
         [:input.kombo {:class     (when lomake? "form-control")
                        :type      "text" :value nykyinen-arvo
                        :on-focus  on-focus
                        :on-change #(reset! data (-> % .-target .-value))}]
         [:button {:on-click #(do (swap! auki not) nil)}
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
(def +aika-regex+ #"\d{1,2}(:\d*)?")

;; pvm-tyhjana ottaa vastaan pvm:n siitä kuukaudesta ja vuodesta, jonka sivu
;; halutaan näyttää ensin
(defmethod tee-kentta :pvm [{:keys [pvm-tyhjana rivi focus on-focus lomake? irrallinen? pvm-sijainti]} data]

  (let [;; pidetään kirjoituksen aikainen ei validi pvm tallessa
        p @data
        pvm-sijainti (or pvm-sijainti :alas)
        teksti (atom (if p
                       (pvm/pvm p)
                       ""))

        ;; picker auki?
        auki (atom false)

        teksti-paivamaaraksi! (fn [data t]
                                (reset! teksti t)
                                (if (str/blank? t)
                                  (reset! data nil))
                                (when-not focus
                                  (when-let [d (pvm/->pvm t)]
                                    (reset! data d))))

        muuta! (fn [data t]
                 (when
                   (or
                     (re-matches +pvm-regex+ t)
                     (str/blank? t))
                   (reset! teksti t))
                 (if (str/blank? t)
                   (reset! data nil)))

        sijainti (atom nil)]
    (komp/luo
      (komp/klikattu-ulkopuolelle #(reset! auki false))
      {:component-will-receive-props
       (fn [this _ {:keys [focus] :as s} data]
         (let [p @data]
           (reset! teksti (if p
                            (pvm/pvm p)
                            ""))))

       :component-did-mount
       (when (or lomake? irrallinen?)
         (fn [this]
           (let [sij (some-> this
                             r/dom-node
                             (.getElementsByTagName "input")
                             (aget 0)
                             yleiset/sijainti-sailiossa)
                 [x y w h] sij]
             (when x
               (if lomake?
                 ;; asemointi, koska col-sm-* divissä
                 (reset! sijainti [15 (+ y h (if (= lomake? :rivi) 39 0)) w])

                 ;; irrallinen suoraan sijainnin mukaan
                 (reset! sijainti [(- w) (+ y h) w]))))))


       :reagent-render
       (fn [_ data]
         (let [nykyinen-pvm @data
               nykyinen-teksti @teksti
               pvm-tyhjana (or pvm-tyhjana (constantly nil))
               naytettava-pvm (or
                                (pvm/->pvm nykyinen-teksti)
                                nykyinen-pvm
                                (pvm-tyhjana rivi))]
           [:span.pvm-kentta
            {:on-click #(do (reset! auki true) nil)
             :style    {:display "inline-block"}}
            [:input.pvm {:class       (when lomake? "form-control")
                         :value       nykyinen-teksti
                         :on-focus    #(do (when on-focus (on-focus)) (reset! auki true) %)
                         :on-change   #(muuta! data (-> % .-target .-value))
                         ;; keycode 9 = Tab. Suljetaan datepicker kun painetaan tabia.
                         :on-key-down #(when (or (= 9 (-> % .-keyCode)) (= 9 (-> % .-which)))
                                        (reset! auki false)
                                        %)
                         :on-blur     #(do
                                        (teksti-paivamaaraksi! data (-> % .-target .-value)))}]
            (when @auki
              [pvm-valinta/pvm {:valitse #(do (reset! auki false)
                                              (reset! data %)
                                              (reset! teksti (pvm/pvm %)))
                                :style   (case pvm-sijainti
                                           :oikea  {:top 0 :left"100%"}
                                           :ylos {:bottom "100%" :left 0}
                                           :ylos-vasen {:bottom "100%" :right 0}
                                           :alas {:top "100%" :left 0})
                                :pvm     naytettava-pvm
                                :sijainti @sijainti}])]))})))

(defmethod nayta-arvo :pvm [_ data]
  [:span (if-let [p @data]
           (pvm/pvm p)
           "")])

(defmethod tee-kentta :pvm-aika [{:keys [pvm-tyhjana rivi focus on-focus lomake? leveys pvm-sijainti]} data]

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
        sijainti (atom nil)
        pvm-sijainti (or pvm-sijainti :alas)]
    (komp/luo
      (komp/klikattu-ulkopuolelle #(reset! auki false))
      {:component-will-receive-props
       (fn [this _ {:keys [focus] :as s} data]
         (when-not focus
           (reset! auki false))
         (swap! pvm-teksti #(if-let [p @data]
                             (pvm/pvm p)
                             %)))

       :component-did-mount
       (when lomake? (fn [this]
                       (let [sij (some-> this
                                         r/dom-node
                                         (.getElementsByTagName "input")
                                         (aget 0)
                                         yleiset/sijainti-sailiossa)
                             [x y w h] sij]
                         (when x
                           ;; PENDIN: kovakoodattua asemointia!
                           (reset! sijainti [15 (+ y h (if (= lomake? :rivi) 39))
                                             w h])))))}

      (fn [_ data]
        (let [aseta! (fn []
                       (let [pvm @pvm-teksti
                             aika @aika-teksti
                             p (pvm/->pvm-aika (str pvm " " aika))]
                         (when-not (some false? @pvm-aika-koskettu)
                           (if p
                             (reset! data p)
                             (reset! data nil)))))

              muuta-pvm! (fn [t]
                           (when (or
                                  (str/blank? t)
                                  (re-matches +pvm-regex+ t))
                             (reset! pvm-teksti t)))

              muuta-aika! (fn [t]
                            (when (or (str/blank? t)
                                      (re-matches +aika-regex+ t))
                              (reset! aika-teksti t)))

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
          [:span.pvm-kentta
           [:table
            [:tbody
             [:tr
              [:td
               [:input.pvm {:class       (when lomake? "form-control")
                            :placeholder "pp.kk.vvvv"
                            :on-click    #(do (.stopPropagation %)
                                              (.preventDefault %)
                                              (reset! auki true)
                                              nil)
                            :value       nykyinen-pvm-teksti
                            :on-focus    #(do (on-focus) (reset! auki true))
                            :on-change   #(muuta-pvm! (-> % .-target .-value))
                            ;; keycode 9 = Tab. Suljetaan datepicker kun painetaan tabia.
                            :on-key-down #(when (or (= 9 (-> % .-keyCode)) (= 9 (-> % .-which)))
                                            (reset! auki false)
                                            %)
                            :on-blur     #(do (koske-pvm!) (aseta!))}]
               (when @auki
                 [pvm-valinta/pvm {:valitse #(do (reset! auki false)
                                                 (muuta-pvm! (pvm/pvm %))
                                                 (koske-pvm!)
                                                 (aseta!))
                                   :style (case pvm-sijainti
                                            :oikea {:top 0 :left "100%"}
                                            :ylos {:bottom "100%" :left 0}
                                            :ylos-vasen {:bottom "100%" :right 0}
                                            :alas {:top "100%" :left 0})
                                   :pvm     naytettava-pvm}])]
              [:td
               [:input {:class       (str (when lomake? "form-control")
                                          (when (and (not (re-matches +aika-regex+ nykyinen-aika-teksti))
                                                     (pvm/->pvm nykyinen-pvm-teksti))
                                            " puuttuva-arvo"))
                        :placeholder "tt:mm"
                        :size        5 :max-length 5
                        :value       nykyinen-aika-teksti
                        :on-change   #(muuta-aika! (-> % .-target .-value))
                        :on-blur     #(do (koske-aika!) (aseta!))}]

               ]]]]

           (when (and (= :alas pvm-sijainti) @auki)
             [:div.aikavalinta
              [pvm-valinta/pvm {:valitse  #(do (reset! auki false)
                                               (muuta-pvm! (pvm/pvm %))
                                               (koske-pvm!)
                                               (aseta!))
                                :pvm      naytettava-pvm
                                :sijainti @sijainti
                                :leveys   leveys}]])])))))

(defmethod nayta-arvo :pvm-aika [_ data]
  [:span (if-let [p @data]
           (pvm/pvm-aika p)
           "")])


(defmethod tee-kentta :tierekisteriosoite [{:keys [lomake? sijainti]} data]
  (let [osoite-alussa @data

        hae-sijainti (not (nil? sijainti))
        tr-osoite-ch (chan)

        osoite-ennen-karttavalintaa (atom nil)
        karttavalinta-kaynnissa (atom false)

        edellinen-extent (atom nil)
        nayta-kartalla (fn [arvo]
                         (if (or (nil? arvo) (vkm/virhe? arvo))
                           (kartta/poista-geometria! :tr-valittu-osoite)
                           (do (kartta/nayta-geometria! :tr-valittu-osoite
                                                        {:alue (assoc arvo
                                                                      :stroke {:width 4})
                                                         :type :tr-valittu-osoite})
                               (let [e (geo/extent arvo)]
                                 #_(kartta/keskita-kartta-alueeseen! e)
                                 (reset! edellinen-extent e)))))]
    (when hae-sijainti
      (nayta-kartalla @sijainti)
      (go (loop []
            (let [arvo (<! tr-osoite-ch)]
              (log "VKM/TR: " (pr-str arvo))
              (when arvo
                (do (reset! sijainti (:geometria arvo))
                    (nayta-kartalla (:geometria arvo))
                    (recur)))))))

    (komp/luo
     {:component-will-update
      (fn [_ _ {sijainti :sijainti}]
        (when sijainti
          (nayta-kartalla @sijainti)))}

     (komp/ulos #(do
                   (log "Lopetetaan TR sijaintipäivitys")
                   (async/close! tr-osoite-ch)
                   (kartta/poista-geometria! :tr-valittu-osoite)))

      (fn [{:keys [lomake? sijainti]} data]
        (let [{:keys [numero alkuosa alkuetaisyys loppuosa loppuetaisyys] :as osoite} @data
              muuta! (fn [kentta]
                       #(let [v (-> % .-target .-value)
                              tr (if (and (not (= "" v))
                                          (re-matches #"\d*" v))
                                   (swap! data assoc kentta (js/parseInt (-> % .-target .-value)))
                                   (swap! data assoc kentta nil))]))
              blur (when hae-sijainti
                     #(when osoite
                        (go (>! tr-osoite-ch osoite))))
              kartta? @karttavalinta-kaynnissa]
          [:span.tierekisteriosoite-kentta
           [:table
            [:tbody
             [:tr
              [:td [:input.tierekisteri {:class       (when lomake? "form-control")
                                         :size        5 :max-length 10
                                         :placeholder "Tie#"
                                         :value       numero
                                         :disabled kartta?
                                         :on-change   (muuta! :numero)
                                         :on-blur blur}]]
              [:td [:input.tierekisteri {:class       (when lomake? "form-control")
                                         :size        5 :max-length 10
                                         :placeholder "aosa"
                                         :value       alkuosa
                                         :disabled kartta?
                                         :on-change   (muuta! :alkuosa)
                                         :on-blur blur}]]
              [:td [:input.tierekisteri {:class       (when lomake? "form-control")
                                         :size        5 :max-length 10
                                         :placeholder "aet"
                                         :value       alkuetaisyys
                                         :disabled kartta?
                                         :on-change   (muuta! :alkuetaisyys)
                                         :on-blur blur}]]
              [:td [:input.tierekisteri {:class       (when lomake? "form-control")
                                         :size        5 :max-length 10
                                         :placeholder "losa"
                                         :value       loppuosa
                                         :disabled kartta?
                                         :on-change   (muuta! :loppuosa)
                                         :on-blur blur}]]
              [:td [:input.tierekisteri {:class       (when lomake? "form-control")
                                         :size        5 :max-length 10
                                         :placeholder "let"
                                         :value       loppuetaisyys
                                         :disabled kartta?
                                         :on-change   (muuta! :loppuetaisyys)
                                         :on-blur blur}]]
              (if-not @karttavalinta-kaynnissa
                [:td [:button.nappi-ensisijainen {:on-click #(do (.preventDefault %)
                                                                 (reset! osoite-ennen-karttavalintaa osoite)
                                                                 (reset! data {})
                                                                 (reset! karttavalinta-kaynnissa true))}
                      (ikonit/map-marker) " Valitse kartalta"]]
                [tr/karttavalitsin {:kun-peruttu #(do
                                                    (reset! data @osoite-ennen-karttavalintaa)
                                                    (reset! karttavalinta-kaynnissa false))
                                    :paivita #(swap! data merge %)
                                    :kun-valmis #(do
                                                   (reset! data %)
                                                   (reset! karttavalinta-kaynnissa false)
                                                   (go (>! tr-osoite-ch %)))}])

              (when-let [sijainti (and hae-sijainti @sijainti)]
                (when (vkm/virhe? sijainti)
                  [:td [:div.virhe (vkm/pisteelle-ei-loydy-tieta sijainti)]]))]]]])))))

(defmethod nayta-arvo :tierekisteriosoite [_ data]
  (let [{:keys [numero alkuosa alkuetaisyys loppuosa loppuetaisyys]} @data]
    [:span.tierekisteriosoite
     [:span.tie "Tie " numero] " / "
     [:span.alkuosa alkuosa] " / "
     [:span.alkuetaisyys alkuetaisyys]
     [:span.loppuosa loppuosa] " / "
     [:span.loppuetaisyys loppuetaisyys]]))
         
