(ns harja.ui.kentat
  "UI-input kenttien muodostaminen tyypin perusteella, esim. grid ja lomake komponentteihin."
  (:require [reagent.core :refer [atom] :as r]
            [reagent.ratom :as ratom]
            [harja.pvm :as pvm]
            [harja.ui.dom :as dom]
            [harja.ui.pvm :as pvm-valinta]
            [harja.ui.protokollat :refer [hae]]
            [harja.ui.komponentti :as komp]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.tierekisteri :as tr]
            [harja.ui.sijaintivalitsin :as sijaintivalitsin]
            [harja.ui.yleiset :refer [linkki ajax-loader livi-pudotusvalikko nuolivalinta valinta-ul-max-korkeus-px] :as yleiset]
            [harja.ui.napit :as napit]
            [harja.loki :refer [log logt tarkkaile!] :as loki]
            [harja.tiedot.sijaintivalitsin :as sijaintivalitsin-tiedot]
            [clojure.string :as str]
            [cljs.core.async :refer [<! >! chan] :as async]

            [harja.tiedot.kartta :as kartta]
            [harja.ui.kartta.esitettavat-asiat :refer [maarittele-feature]]
            [harja.views.kartta.tasot :as tasot]
            [harja.geo :as geo]

    ;; Tierekisteriosoitteen muuntaminen sijainniksi tarvii tämän
            [harja.tyokalut.vkm :as vkm]
            [harja.atom :refer [paivittaja]]
            [harja.fmt :as fmt]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.kartta.asioiden-ulkoasu :as asioiden-ulkoasu]
            [harja.ui.yleiset :as y]
            [harja.domain.tierekisteri :as trd]
            [harja.views.kartta.tasot :as karttatasot]
            [harja.tyokalut.big :as big])
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

(defmethod tee-kentta :haku [{:keys [_lahde nayta placeholder pituus lomake? sort-fn disabled?
                                     kun-muuttuu hae-kun-yli-n-merkkia vayla-tyyli? monivalinta? salli-kirjoitus?
                                     tarkkaile-ulkopuolisia-muutoksia? monivalinta-teksti piilota-checkbox? piilota-dropdown?
                                     hakuikoni? input-id]} data]
  (when monivalinta?
    (assert (ifn? monivalinta-teksti) "Monivalintahakukentällä pitää olla funktio monivalinta-teksti!"))
  (let [nyt-valittu @data
        teksti (atom (cond
                       (and (not monivalinta?) nyt-valittu)
                       ((or nayta str) nyt-valittu)

                       (and monivalinta? (fn? monivalinta-teksti))
                       (monivalinta-teksti nyt-valittu)

                       :else ""))
        monivalinta-valitse! (fn [valinta]
                               (if (and (some? valinta) (some #{valinta} @data))
                                 (swap! data #(remove #{valinta} %))
                                 (swap! data conj valinta)))
        tulokset (atom nil)
        valittu-idx (atom nil)
        hae-kun-yli-n-merkkia (or hae-kun-yli-n-merkkia 2)
        edellinen-data (atom @data)]
    (komp/luo
      (komp/klikattu-ulkopuolelle #(reset! tulokset nil))
      (fn [{:keys [lahde disabled?]} data]

        (when (and
                tarkkaile-ulkopuolisia-muutoksia?
                (nil? @data)
                (not= @edellinen-data @data))
          (reset! teksti (if monivalinta?
                           (monivalinta-teksti nil)
                           ((or nayta str) nil))))
        (reset! edellinen-data @data)

        [:div.hakukentta.dropdown {:class (when (some? @tulokset) "open")}

         [:input {:class (cond-> nil
                           lomake? (str "form-control ")
                           vayla-tyyli? (str "input-default komponentin-input ")
                           disabled? (str "disabled"))
                  :value @teksti
                  :id input-id
                  :placeholder placeholder
                  :disabled disabled?
                  :size pituus
                  :on-change #(when (= (.-activeElement js/document) (.-target %))
                                ;; tehdään haku vain jos elementti on fokusoitu
                                ;; IE triggeröi on-change myös ohjelmallisista muutoksista
                                (let [v (-> % .-target .-value str/triml)]
                                  ;; Kun monivalinta, tai sallitaan kirjoitus, älä resetoi dataa
                                  (when (and
                                          (not monivalinta?)
                                          (not salli-kirjoitus?)) (reset! data nil))
                                  (reset! teksti v)
                                  (when kun-muuttuu (kun-muuttuu v))
                                  (if (> (count v) hae-kun-yli-n-merkkia)
                                    (do (reset! tulokset :haetaan)
                                      (go (let [tul (<! (hae lahde v))]
                                            (reset! tulokset tul)
                                            (reset! valittu-idx nil)
                                            ;; Jos sallitaan kirjoitus, aseta data kentän arvoksi
                                            (when (and
                                                    (empty? tul)
                                                    salli-kirjoitus?)
                                              (reset! data v)))))
                                    (do
                                      (when salli-kirjoitus? (reset! data v))
                                      (reset! tulokset nil)))))
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
                                        (if monivalinta?
                                          (reset! teksti (monivalinta-teksti (monivalinta-valitse! v)))
                                          (do
                                            (reset! teksti ((or nayta str) (reset! data v)))
                                            (reset! tulokset nil)))
                                        (when kun-muuttuu (kun-muuttuu nil))))))}]

         (when (and
                 (zero? hae-kun-yli-n-merkkia)
                 (not piilota-dropdown?))
           [:button.nappi-hakualasveto
            {:on-click #(go
                          (reset! tulokset (<! (hae lahde "")))
                          (reset! valittu-idx nil))
             :disabled disabled?}
            (if hakuikoni?
              [:span.livicon-search]
              [:span.livicon-chevron-down])])

         (let [nykyiset-tulokset (if (and sort-fn (vector? @tulokset))
                                   (sort-by sort-fn @tulokset)
                                   @tulokset)
               haetaan? (= nykyiset-tulokset :haetaan)
               idx @valittu-idx]

           [:ul.hakukentan-lista.dropdown-menu {:role "menu" :style {:max-height valinta-ul-max-korkeus-px}
                                                :class (when (and
                                                               ;; Kun sallitaan oman selitteen asetus, "Ei tuloksia" ei tarvitse näyttää
                                                               salli-kirjoitus?
                                                               (not haetaan?)
                                                               (or
                                                                 (empty? nykyiset-tulokset)
                                                                 (nil? nykyiset-tulokset)))
                                                         "piilotettu-elementti")}

            (if haetaan?
              [:li {:role "presentation"} (ajax-loader) " haetaan: " @teksti]
              (if (empty? nykyiset-tulokset)
                [:span.ei-hakutuloksia "Ei tuloksia"]
                (doall (map-indexed (fn [i t]
                                      ^{:key (hash t)}
                                      [:li {:class [(when (= i idx) "korostettu") "padding-left-8"
                                                    "harja-alasvetolistaitemi display-flex items-center klikattava"]
                                            :role "presentation"}
                                       [tee-kentta
                                        {:tyyppi :checkbox
                                         :teksti ((or nayta str) t)
                                         :piilota-checkbox? piilota-checkbox?
                                         :valitse! #(do
                                                      (.preventDefault %)
                                                      (if monivalinta?
                                                        (reset! teksti (monivalinta-teksti (monivalinta-valitse! t)))
                                                        (do
                                                          (reset! teksti ((or nayta str) (reset! data t)))
                                                          (reset! tulokset nil)))
                                                      (when kun-muuttuu (kun-muuttuu nil)))}
                                        (or (= t @data) (some #{t} @data))]])
                         nykyiset-tulokset))))])]))))


(defn placeholder [{:keys [placeholder placeholder-fn rivi] :as kentta} data]
  (or placeholder
    (and placeholder-fn (placeholder-fn rivi))))

(defmethod tee-kentta :string [{:keys [nimi pituus-max vayla-tyyli? pituus-min virhe? regex focus on-focus on-blur lomake? toiminta-f disabled? vihje elementin-id muokattu?]
                                :as kentta} data]
  [:input {:class (cond-> nil
                    (and lomake?
                      (not vayla-tyyli?)) (str "form-control ")
                    vayla-tyyli? (str "input-" (if (and muokattu? virhe?) "error-" "") "default komponentin-input ")
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
           :id (or elementin-id nil)
           :max-length pituus-max}])

(defmethod tee-kentta :linkki [opts data]
  [tee-kentta (assoc opts :tyyppi :string) data])

(defmethod nayta-arvo :linkki [_ data]
  [:a {:href @data} @data])


;; Pitkä tekstikenttä käytettäväksi lomakkeissa, ei sovellu hyvin gridiin
;; pituus-max oletusarvo on 256, koska se on toteuman lisätiedon tietokantasarakkeissa
(defmethod tee-kentta :text [{:keys [placeholder nimi koko on-focus on-blur lomake?
                                     disabled? pituus-max toiminta-f]} data]
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
             (when (> erotus 1) ;; IE11 näyttää aluksi 24 vs 25
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

(defn- normalisoi-numero [n salli-whitespace?]
  (when n (-> n
            ;; Poistetaan whitespace, jos ei sallittu
            (as-> n n
              (if-not salli-whitespace? (str/replace n #"\s" "")
                                        n))
            ;; Poistetaan mahd. euromerkki lopusta
            (str/replace #"€$" "")

            ;; Poistetaan ympäröivä whitespace joka tapauksessa
            (str/trim))))

(def +desimaalin-oletus-tarkkuus+ 2)

(defn numero-fmt [{:keys [kokonaisluku? desimaalien-maara min-desimaalit max-desimaalit fmt] :as kentta}]
  (cond
    fmt
    fmt

    kokonaisluku?
    #(fmt/desimaaliluku-opt % 0)

    (contains? kentta :desimaalien-maara)
    #(fmt/desimaaliluku-opt % desimaalien-maara)

    (or (contains? kentta :min-desimaalit) (contains? kentta :max-desimaalit))
    #(fmt/desimaaliluku-opt % min-desimaalit max-desimaalit false)

    :else
    nil))

;; desimaalien-maara asettaa min-desimaalit ja max-desimaalit samaan arvoon
;; ks. harja.fmt/desimaali-fmt
(defmethod tee-kentta :numero [{:keys [elementin-id oletusarvo validoi-kentta-fn koko input-luokka
                                       desimaalien-maara min-desimaalit max-desimaalit on-key-down
                                       veda-oikealle? luokka teksti-oikealla]
                                :as kentta} data]
  (let [fmt (or (numero-fmt kentta) str)
        teksti (atom nil)
        kokonaisosan-maara (or (:kokonaisosan-maara kentta) 10)
        id (or elementin-id (gensym))]
    (komp/luo
      (komp/nimi "Numerokenttä")
      (komp/piirretty #(when (and oletusarvo (nil? @data)) (reset! data oletusarvo)))
      (fn [{:keys [lomake? kokonaisluku? vaadi-ei-negatiivinen? vaadi-negatiivinen? toiminta-f on-blur on-focus
                   disabled? vayla-tyyli? virhe? yksikko validoi-kentta-fn salli-whitespace? disabloi-autocomplete? muokattu?] :as kentta} data]
        (let [yksikko (if-not yksikko teksti-oikealla yksikko)
              nykyinen-data @data
              nykyinen-teksti (or @teksti
                                  (normalisoi-numero (fmt nykyinen-data) salli-whitespace?)
                                  "")
              kokonaisluku-re-pattern (re-pattern (str "-?\\d{1," kokonaisosan-maara "}"))
              desimaalien-maara (cond
                                  (contains? kentta :desimaalien-maara) ; Salli nil-arvo
                                  desimaalien-maara

                                  (contains? kentta :max-desimaalit)
                                  max-desimaalit

                                  (contains? kentta :min-desimaalit)
                                  min-desimaalit

                                  :else
                                  +desimaalin-oletus-tarkkuus+)
              desimaaliluku-re-pattern (re-pattern (str
                                                     "-?\\d{1,"
                                                     kokonaisosan-maara
                                                     "}((\\.|,)\\d{0,"
                                                     desimaalien-maara
                                                     "})?"))]
          
          [:span.numero
           [:input {:id id
                    :class (cond-> nil
                             (and lomake?
                               (not vayla-tyyli?)) (str "form-control ")
                             vayla-tyyli? (str "input-" (if (and muokattu? virhe?) "error-" "") "default komponentin-input ")
                             disabled? (str "disabled")
                             input-luokka (str " " input-luokka)
                             veda-oikealle? (str " veda-oikealle"))
                    :style (when (and veda-oikealle? yksikko)
                             {:padding-right (str "calc(19px + " (count yksikko) "ch")})
                    :type "text"
                    :disabled disabled?
                    :auto-complete (if disabloi-autocomplete? "off" "on")
                    :placeholder (placeholder kentta data)
                    :size (or koko nil)
                    :on-key-down (or on-key-down nil)
                    :on-focus #(when on-focus (on-focus))
                    :on-blur #(do
                                (when on-blur
                                  (on-blur %))
                                (reset! teksti nil))
                    :value nykyinen-teksti
                    :on-change #(let [v (normalisoi-numero (-> % .-target .-value) salli-whitespace?)
                                      v (cond
                                          vaadi-ei-negatiivinen?
                                          (str/replace v #"-" "")
                                          vaadi-negatiivinen?
                                          (if (= (first v) \-)
                                            v
                                            (str "-" v))
                                          :default v)]
                                  (when (and
                                          (or (nil? validoi-kentta-fn)
                                            (validoi-kentta-fn v))
                                          (or (= v "")
                                            (when-not vaadi-ei-negatiivinen? (= v "-"))
                                            (re-matches (if kokonaisluku?
                                                          kokonaisluku-re-pattern
                                                          desimaaliluku-re-pattern)
                                                ;; Matchataan whitespacesta huolimatta
                                              (str/replace v #"\s" ""))))
                                    (reset! teksti v)

                                    ;; Numeron parsimista varten pitää poistaa whitespace,
                                    ;; vaikka haluttaisiin näyttää se.
                                    (let [v (str/replace v #"\s" "")
                                          numero (if kokonaisluku?
                                                   (js/parseInt v)
                                                   (js/parseFloat (str/replace v #"," ".")))]
                                      (if (not (js/isNaN numero))
                                        (reset! data numero)
                                        (reset! data nil))
                                      (when toiminta-f
                                        (toiminta-f (when-not (js/isNaN numero)
                                                      numero))))))}]
           (when (and yksikko vayla-tyyli?)
             [:span.sisainen-label.black-lighter {:style 
                                                  {:margin-left (* -1 (+ 25 (* (- (count yksikko) 2) 5)))
                                                   :margin-top "10px"}} yksikko])])))))

(defmethod nayta-arvo :numero [{:keys [jos-tyhja salli-whitespace? yksikko] :as kentta} data]
 (let [fmt (or (numero-fmt kentta) #(fmt/desimaaliluku-opt % +desimaalin-oletus-tarkkuus+))]
    [:span (if (and jos-tyhja (nil? @data))
             jos-tyhja
             (normalisoi-numero (fmt @data) salli-whitespace?))
     (when yksikko
       (str " " yksikko))]))

(defmethod tee-kentta :negatiivinen-numero [kentta data]
  [tee-kentta (assoc kentta :vaadi-negatiivinen? true
                            :tyyppi :numero) data])

(defmethod nayta-arvo :negatiivinen-numero [kentta data]
  [nayta-arvo (assoc kentta :tyyppi :numero) data])


(defmethod tee-kentta :positiivinen-numero [kentta data]
  [tee-kentta (assoc kentta :vaadi-ei-negatiivinen? true
                            :tyyppi :numero) data])

(defmethod nayta-arvo :positiivinen-numero [kentta data]
  [nayta-arvo (assoc kentta :tyyppi :numero) data])

(defmethod tee-kentta :euro [{:keys [fmt teksti-oikealla] :as kentta} data]
  [tee-kentta (assoc kentta
                :tyyppi :numero
                :fmt (or fmt (partial fmt/euro-opt false))
                :salli-whitespace? true
                :yksikko (or teksti-oikealla "€")
                :desimaalien-maara 2
                :veda-oikealle? true)
   data])

(defmethod nayta-arvo :euro [{:keys [fmt] :as kentta} data]
  [nayta-arvo (assoc kentta
                :tyyppi :numero
                :fmt (or fmt (partial fmt/euro-opt false))
                :yksikko "€"
                :salli-whitespace? true) data])

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



(defmethod tee-kentta :puhelin [{:keys [on-focus on-blur pituus lomake? placeholder disabled? vayla-tyyli? muokattu? virhe?] :as kentta} data]
  [:input {:class (cond-> nil
                    (and lomake?
                      (not vayla-tyyli?)) (str "form-control ")
                    vayla-tyyli? (str "input-" (if (and muokattu? virhe?) "error-" "") "default komponentin-input ")
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

(defn wrappaa-prevent-default [fn!]
  (fn [event]
    (do
      (.preventDefault event)
      (.stopPropagation event)
      (fn! event))))

(defn vayla-checkbox
  [{:keys [input-id lukutila? disabled? arvo data piilota-checkbox?
           teksti valitse! checkbox-style label-luokka label-id indeterminate]}]
  (let [arvo (or arvo false)
        input-id (or input-id
                     (gensym "checkbox-input-id-"))
        label-id (or label-id
                     (gensym "checkbox-label-id-"))]
    [:div
     [:input
      {:id input-id
       :class (y/luokat 
                (if piilota-checkbox? "piilotettu-checkbox" "vayla-checkbox") 
                "check" 
                (when lukutila? "lukutila"))
       :type "checkbox"
       :disabled disabled?
       :checked arvo
       :on-click #(.stopPropagation %)
       :on-change (wrappaa-prevent-default
                    (or valitse!
                        #(let [valittu? (-> % .-target .-checked)]
                           (reset! data valittu?))))}]
     [:label.checkbox-label {:on-click #(.stopPropagation %)
                             :id label-id
                             :class (y/luokat
                                      label-luokka
                                      (when disabled? "disabled")
                                      (when indeterminate "indeterminate")
                                      (when piilota-checkbox? "autofill-teksti"))
                             :on-key-down #()
                             :for input-id
                             :style (or checkbox-style {})}
      teksti]]))

(defn kylla-ei-valinta
  "Kolmitilainen valinta: [✓] [?] [✗]"
  [{:keys [on-click ladataan? disabled? vaihtoehdot]
    :or {vaihtoehdot [{:vastaus true
                       :valittu-luokka "kylla-valittu"
                       :valitsematta-luokka "kylla-valitsematta"
                       :ikoni ikonit/harja-icon-status-completed}
                      {:vastaus nil
                       :valittu-luokka "odottaa"
                       :valitsematta-luokka "odottaa-valitsematta"
                       :ikoni ikonit/harja-icon-status-help}
                      {:vastaus false
                       :valittu-luokka "ei-valittu"
                       :valitsematta-luokka "ei-valitsematta"
                       :ikoni ikonit/harja-icon-status-denied}]
         on-click identity}}
   data]
  [y/himmennys {:himmenna? disabled?}
   [:div.ke-valinta
    (for [{:keys [vastaus valittu-luokka valitsematta-luokka ikoni]} vaihtoehdot]
      (let [valittu? (= vastaus data)
            nayta-spinner? (and valittu? ladataan?)]
        ^{:key (str "ke-valinta-" vastaus)}
        [:div.ke-vastaus {:class (if valittu? valittu-luokka valitsematta-luokka)
                          :on-click #(on-click vastaus)}
         ;; Näytä joko spinner tai ikoni
         ;; Ei poisteta elementtejä DOMista, koska muuten klikattu-ulkopuolelle ei toimi oikein
         [:div {:class (when-not nayta-spinner? "hidden")}
          [y/ajax-loader-pieni]]
         [:div {:class (when nayta-spinner? "hidden")}
          [ikoni]]]))]])

;; Luo usean checkboksin, jossa valittavissa N-kappaleita vaihtoehtoja. Arvo on setti ruksittuja asioita
(defmethod tee-kentta :checkbox-group
  [{:keys [vaihtoehdot vaihtoehto-nayta valitse-kaikki?
           tyhjenna-kaikki? nayta-rivina? disabloi tasaa
           muu-vaihtoehto muu-kentta palstoja rivi-solun-tyyli
           valitse-fn valittu-fn label-luokka]} data]
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
           checkbox (fn [vaihtoehto]
                      [vayla-checkbox {:arvo (valitut vaihtoehto)
                                       :teksti (vaihtoehto-nayta vaihtoehto)
                                       :disabled? (if disabloi
                                                    (disabloi valitut vaihtoehto)
                                                    false)
                                       :label-luokka (or label-luokka "margin-top-16")
                                       :valitse! #(swap! data valitse vaihtoehto (not (valitut vaihtoehto)))}])
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
(defmethod tee-kentta :checkbox [{:keys [teksti nayta-rivina? label-luokka piilota-checkbox?
                                         vayla-tyyli? disabled? iso-clickalue? label-id input-id]} data]
  (let [boolean-arvo? (not (or (instance? ratom/RAtom data) (instance? ratom/Wrapper data) (instance? ratom/RCursor data)))
        input-id (or input-id (str "harja-checkbox-" (gensym)))
        paivita-valitila #(when-let [node (.getElementById js/document input-id)]
                            (set! (.-indeterminate node)
                                  (= @data ::indeterminate)))]
    (komp/luo
      (when-not boolean-arvo? (komp/piirretty paivita-valitila))
      (when-not boolean-arvo? (komp/kun-muuttui paivita-valitila))
      (fn [{:keys [teksti nayta-rivina? disabled? iso-clickalue? valitse! label-luokka]} data]
        (let [_ (when boolean-arvo? (assert (ifn? valitse!) "Jos checkboxin datan tyyppi on boolean atomin sijasta, valitse! pitää olla funktio"))
              arvo (cond
                     boolean-arvo? data
                     (nil? @data) false
                     :default @data)]
          [:div.boolean {:style {:padding (when iso-clickalue?
                                            "14px")}
                         :on-click (when
                                     (and (not disabled?)
                                          iso-clickalue?)
                                     #(do
                                        (.stopPropagation %)
                                        (swap! data not)))}
           (let [checkbox [vayla-checkbox {:data data
                                           :piilota-checkbox? piilota-checkbox?
                                           ;; label- ja input-id on tarkoituksella jätetty toistamatta sisemmmässä funktiossa.
                                           :input-id input-id
                                           :label-id label-id
                                           :teksti teksti
                                           :disabled? disabled?
                                           :valitse! valitse!
                                           :arvo arvo
                                           :label-luokka label-luokka
                                           :indeterminate (= ::indeterminate data)}]]
             (if nayta-rivina?
               [:table.boolean-group
                [:tbody
                 [:tr
                  [:td checkbox]]]]
               checkbox))])))))

;; vayla-tyylinen checkbox halutaan näyttää myös lomakkeella myös lukutilassa,
;; Pidetään muuntyylisillä default :nayta-arvo-toiminnallisuus.
(defmethod nayta-arvo :checkbox [{:keys [teksti]} data]
  [:div.boolean
   (vayla-checkbox {:data data
                    :input-id (str "harja-checkbox" (gensym))
                    :teksti teksti
                    :disabled? true
                    :lukutila? true ;; read only tilan ero vain disablediin: ei ole niin "harmaa". Kumpaakaan ei voi muokata
                    :arvo @data})])

(defn- vayla-radio [{:keys [id teksti ryhma valittu? oletus-valittu? disabloitu? kaari-flex-row? muutos-fn opts radio-luokka]}]
  ;; React-varoitus korjattu: saa olla vain checked vai default-checked, ei molempia
  (let [checked (if oletus-valittu?
                  {:default-checked oletus-valittu?}
                  {:checked valittu?})
        selite (:selite opts)
        valittu-komponentti (:valittu-komponentti opts)]
    [:<>
    [:div {:class (if (false? kaari-flex-row?)
                    (str " flex-row"))}
     [:input#kulu-normaali.vayla-radio
      (merge {:id id
              :type :radio
              :name ryhma
              :disabled disabloitu?
              :class radio-luokka
              :on-change muutos-fn}
             checked)]
     [:label (merge {:style (when (false? kaari-flex-row?) {:flex-shrink 0 :flex-grow 1})}
                    {:for id}) teksti]]
    [:div.vayla-radio-lapsi
     (when selite
       [:div.caption
        selite])
     (when (and (some true? (vals checked)) valittu-komponentti)
       valittu-komponentti)]]))

(defmethod tee-kentta :radio-group [{:keys [vaihtoehdot vaihtoehto-nayta vaihtoehto-arvo nayta-rivina?
                                            oletusarvo vayla-tyyli? disabloitu? valitse-fn radio-luokka
                                            kaari-flex-row? vaihtoehto-opts space-valissa?]}
                                    data]
  (let [vaihtoehto-nayta (or vaihtoehto-nayta
                             #(clojure.string/capitalize (name %)))
        valittu (or @data nil)]
    ;; Jos oletusarvo on annettu, se sisältyy vaihtoehtoihin, ja mitään ei ole valittu,
    ;; valitaan oletusarvo
    (when (and (nil? valittu)
               oletusarvo
               (some (partial = oletusarvo) vaihtoehdot))
      (reset! data oletusarvo))
    [:div {:style {:flex-shrink 0 :flex-grow 1}}
     (let [group-id (gensym (str "radio-group-"))
           radiobuttonit (doall
                           (for [vaihtoehto vaihtoehdot
                                 :let [vaihtoehdon-arvo (if vaihtoehto-arvo
                                                          (vaihtoehto-arvo vaihtoehto)
                                                          vaihtoehto)
                                       opts (get vaihtoehto-opts vaihtoehto)]]
                             (if vayla-tyyli?
                               ^{:key (str "radio-group-" (vaihtoehto-nayta vaihtoehto))}
                               [vayla-radio {:teksti (vaihtoehto-nayta vaihtoehto)
                                             :muutos-fn #(let [valittu? (-> % .-target .-checked)]
                                                           (do
                                                             (when valitse-fn
                                                               (valitse-fn vaihtoehdon-arvo))
                                                             (when valittu?
                                                               (reset! data vaihtoehdon-arvo))))
                                             :disabloitu? disabloitu?
                                             :valittu? (or (and (nil? valittu) (= vaihtoehto oletusarvo))
                                                           (= valittu vaihtoehdon-arvo))
                                             :ryhma group-id
                                             :id (gensym (str "radio-group-" (vaihtoehto-nayta vaihtoehto)))
                                             :opts opts
                                             :kaari-flex-row? kaari-flex-row?
                                             :radio-luokka radio-luokka}]
                               ^{:key (str "radio-group-" (vaihtoehto-nayta vaihtoehto))}
                               [:div {:class (y/luokat "radio" radio-luokka)}
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
         [:div {:style {:gap (if space-valissa? "22px" "0px") :display "flex" :flex-direction "row" :flex-wrap "wrap"
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
            pakollinen? tarkenne muokattu? valitse-oletus?]} data]
    ;; valinta-arvo: funktio rivi -> arvo, jolla itse lomakken data voi olla muuta kuin valinnan koko item
    ;; esim. :id
    (assert (or valinnat valinnat-fn) "Anna joko valinnat tai valinnat-fn")

   (let [nykyinen-arvo (cond
                         (and
                           valitse-oletus?
                           valinta-arvo
                           (= 1 (count valinnat)))
                         (valinta-arvo (first valinnat))
                         
                         (and
                           valitse-oletus?
                           (not valinta-arvo)
                           (= 1 (count valinnat)))
                         (first valinnat)
                         
                         :else
                         @data)
         _ (when (and valitse-oletus? (not= nykyinen-arvo @data)) (reset! data nykyinen-arvo))
          ;; Valintalistaus pitää olla muodostettuna ennen valinnan tekemistä
          valinnat (or valinnat (valinnat-fn rivi))
          valinta (when valinta-arvo
                    (some #(when (= (valinta-arvo %) nykyinen-arvo) %) valinnat))
          opts {:class (y/luokat "alasveto-gridin-kentta" alasveto-luokka (y/tasaus-luokka tasaa)
                                 (when (and linkki-fn linkki-icon)
                                   "linkin-vieressa"))
                :valinta (if valinta-arvo
                           valinta
                           nykyinen-arvo)
                :valitse-fn #(reset! data
                                     (if valinta-arvo
                                       (valinta-arvo %)
                                       %))
                :fokus-klikin-jalkeen? fokus-klikin-jalkeen?
                :nayta-ryhmat nayta-ryhmat
                :ryhmittely ryhmittely
                :ryhman-otsikko ryhman-otsikko
                :virhe? virhe?
                :on-focus on-focus
                :on-blur on-blur
                :format-fn (if (empty? valinnat)
                             (or jos-tyhja-fn (constantly (or jos-tyhja "Ei valintoja")))
                             (or (and valinta-nayta #(valinta-nayta % true)) str))
                :disabled disabled?
                :muokattu? muokattu?
                :pakollinen? pakollinen?
                :vayla-tyyli? vayla-tyyli?
                :elementin-id elementin-id
                :tarkenne tarkenne}]
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
         (if disabled?
           [:div.disabled-valinta {:on-click #(linkki-fn nykyinen-arvo)}
            (or (and valinta-nayta (valinta-nayta valinta))
                nykyinen-arvo)]
           [livi-pudotusvalikko opts valinnat])])))
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
                               :virhe? virhe?
                               :format-fn (if (empty? valinnat)
                                            (or jos-tyhja-fn jos-tyhja-default-fn)
                                            (or valinta-nayta str))
                               :disabled disabled?
                               :data-cy data-cy
                               :vayla-tyyli? vayla-tyyli?
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


(defn- tee-ikoni-komponentti
  [[tagi optiot] auki]
  (let [{:keys [class]} optiot
        input-optiot (dissoc optiot :class)]
    [:span {:class (str "ikoni-input " (when auki "fokusoi ") class)}
     [tagi input-optiot]
     (ikonit/calendar)]))

;; pvm-tyhjana ottaa vastaan pvm:n siitä kuukaudesta ja vuodesta, jonka sivu
;; halutaan näyttää ensin
(defmethod tee-kentta :pvm [{:keys [pvm-tyhjana rivi on-focus lomake? pakota-suunta validointi on-datepicker-select vayla-tyyli?]} data]

  (let [;; pidetään kirjoituksen aikainen ei validi pvm tallessa
        p @data
        date->teksti #(if (and % (not (nil? %)) (not= "" %)) (pvm/pvm %) "")
        teksti (atom (date->teksti p))
        ;; pidetään edellinen data arvo tallessa, jotta voidaan muuttaa teksti oikeaksi
        ;; jos annetun data-atomin arvo muuttuu muualla kuin tässä komponentissa
        vanha-data (cljs.core/atom {:data p
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
       (fn [{:keys [on-focus on-blur placeholder rivi validointi on-datepicker-select
                    kentan-tyylit virhe? ikoni-sisaan? muokattu?]} data]
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
                                (pvm-tyhjana rivi))
               elementin-id (str (gensym "pvm-input"))
               input-komponentti [:input {:class (yleiset/luokat (when-not (or kentan-tyylit vayla-tyyli?) "pvm")
                                                                 (cond
                                                                   kentan-tyylit (apply str kentan-tyylit)
                                                                   vayla-tyyli? (str "input-" (if (and muokattu? virhe?) "error-" "") "default ")
                                                                   lomake? "form-control"))
                         :placeholder (or placeholder "pp.kk.vvvv")
                         :value nykyinen-teksti
                         :on-focus #(do (when on-focus (on-focus)) (reset! auki true) %)
                         :on-change #(muuta! data (-> % .-target .-value))
                         ;; Suljetaan datepicker kun painetaan tab + shift tai esc. Enterillä datepickerin saa auki/kiinni.
                         :on-key-down #(do
                                         (when (or (dom/tab+shift-nappaimet? %) (dom/esc-nappain? %))
                                           (teksti-paivamaaraksi! validoi data nykyinen-teksti)
                                           (reset! auki false))
                                         (when (dom/enter-nappain? %)
                                           (teksti-paivamaaraksi! validoi data nykyinen-teksti)
                                           (reset! auki (not @auki))))
                         :on-blur #(let [arvo (.. % -target -value)
                                         pvm (pvm/->pvm arvo)]
                                     (when on-blur
                                       (on-blur %))
                                     (if (and pvm (not (validoi pvm)))
                                       (do (muuta-data! nil)
                                           (reset! teksti ""))
                                       (teksti-paivamaaraksi! validoi data arvo)))
                         :id elementin-id}]]
           (swap! vanha-data assoc :data nykyinen-pvm :muokattu-tassa? false)
           [:span.pvm-kentta
            {:on-click #(do (reset! auki true) nil)
             :style {:display "inline-block"}}
            (if-not ikoni-sisaan?
              input-komponentti
              (tee-ikoni-komponentti input-komponentti @auki))
            (when @auki
              [pvm-valinta/pvm-valintakalenteri {:valitse #(when (validoi %)
                                                             (reset! auki false)
                                                             (muuta-data! %)
                                                             (reset! teksti (pvm/pvm %)))
                                                 :pvm naytettava-pvm
                                                 :pakota-suunta pakota-suunta
                                                 :valittava?-fn (when validoi?
                                                                  validoi)
                                                 :sulje-kalenteri #(do
                                                                     (some-> js/document (.getElementById elementin-id) .focus)
                                                                     (reset! auki false))
                                                 :input-id elementin-id}])]))})))

(defmethod nayta-arvo :pvm [{:keys [jos-tyhja]} data]
  [:span (if-let [p @data]
           ;; On mahdollista, että goog.date.DateTime muuttuu jossakin tilanteessa stringiksi, niin palauta vain se
           (if (string? p)
             p
             (pvm/pvm p))
           (or jos-tyhja ""))])

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
         ;; Jätetään nil-arvo huomiotta, jotta datepicker ei sulkeudu itsestään, kun päivämäärää valitaan ensimmäisen kerran.
         ;; Aiemmin siis oli bugi, jossa ensimmäistä kertaa päivämäärää valittaessa datepicker sulkeutui eikä päivämäärä tullut valituksi.
         (and (not (nil? focus)) (not focus)
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
                               (pvm-tyhjana rivi))
              elementin-id (str (gensym "pvm-aika-input"))]
          [:span.pvm-aika-kentta
           [:div.inline-block
            [:input.pvm {:class (when lomake? "form-control margin-bottom-4")
                         :placeholder "pp.kk.vvvv"
                         :on-click #(do (.stopPropagation %)
                                        (.preventDefault %)
                                        (reset! auki true)
                                        %)
                         :value nykyinen-pvm-teksti
                         :on-focus #(do (when on-focus (on-focus)) (reset! auki true) %)
                         :on-change #(muuta-pvm! (-> % .-target .-value))
                         ;; Suljetaan datepicker kun painetaan tab + shift tai esc. Enterillä datepickerin saa auki/kiinni.
                         :on-key-down #(do
                                         (when (or (dom/tab+shift-nappaimet? %) (dom/esc-nappain? %))
                                           (reset! auki false))
                                         (when (dom/enter-nappain? %)
                                           (reset! auki (not @auki))))
                         :on-blur #(do (when on-blur (on-blur %)) (koske-pvm!) (aseta! false) %)
                         :id elementin-id}]
            (when @auki
              [pvm-valinta/pvm-valintakalenteri {:valitse #(do (reset! auki false)
                                                               (muuta-pvm! (pvm/pvm %))
                                                               (koske-pvm!)
                                                               (aseta! true))
                                                 :pvm naytettava-pvm
                                                 :pakota-suunta pakota-suunta
                                                 :sulje-kalenteri #(do
                                                                     (some-> js/document (.getElementById elementin-id) .focus)
                                                                     (reset! auki false))
                                                 :input-id elementin-id}])]
           [:div.inline-block
            [:input.aika-input {:class (str (when lomake? "form-control")
                                            (when (and (not (re-matches +validi-aika-regex+
                                                                        nykyinen-aika-teksti))
                                                       (pvm/->pvm nykyinen-pvm-teksti))
                                              " puuttuva-arvo"))
                                :placeholder "tt:mm"
                                :size 5 :max-length 5
                                :value nykyinen-aika-teksti
                                :on-change #(muuta-aika! (-> % .-target .-value))
                                :on-blur #(do (koske-aika!) (aseta! false))}]]])))))

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
                                  (when vayla-tyyli? "input-default ")
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

(defn- tierekisterikentat-flex [{:keys [pakollinen? disabled? alaotsikot?]} tie aosa aet losa loppuet tr-otsikot? sijainnin-tyhjennys karttavalinta virhe
                                piste? vaadi-vali?]
  (let [osio (fn [alaotsikko? komponentti otsikko]
               (let [kentta-pakollinen? (cond
                                          (and pakollinen? (or vaadi-vali? (#{"Tie" "Aosa" "Aet"} otsikko)))
                                          true

                                          (and pakollinen?
                                            ;; defaulttina vaaditaan väli, siksi false-tarkistus. On eksplisiittisesti asetettava
                                            ;; vaadi-vali? falseksi, jotta väliä ei vaadita
                                            (false? vaadi-vali?) (#{"Losa" "Let"} otsikko))
                                          false

                                          :else false)]
                 [:div {:class (when pakollinen? "required")}
                  (when-not alaotsikko?
                    [:label.control-label {:class (when-not kentta-pakollinen? "cancel-required-tahti")}
                     [:span.kentan-label otsikko]])
                  komponentti
                  (when alaotsikko?
                    [:span
                     [:span.ala-control-label.kentan-label {:class (when-not kentta-pakollinen? "cancel-required-tahti")}
                      otsikko]])]))]
    (fn [{:keys [pakollinen? disabled? alaotsikot?]} tie aosa aet losa loppuet tr-otsikot? sijainnin-tyhjennys
         karttavalinta virhe piste? vaadi-vali?]

      (let [flex (if alaotsikot?
                   "flex-start"
                   "flex-end")
            top (if alaotsikot?
                  "2px"
                  "0px")]
        [:div
         [:div.tierekisteriosoite-flex
          [osio alaotsikot? tie "Tie"]
          [osio alaotsikot? aosa "Aosa"]
          [osio alaotsikot? aet "Aet"]
          (when-not piste?
            [:<>
             [osio alaotsikot? losa "Losa"]
             [osio alaotsikot? loppuet "Let"]])
          (when virhe
            [:div virhe])
          (when karttavalinta
            [:div {:style {:padding-left "16px" :padding-top top :align-self flex}}
             [:div.karttavalinta
              karttavalinta]])]]))))


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

(defmethod tee-kentta :tierekisteriosoite [{:keys [ala-nayta-virhetta-komponentissa?
                                                   sijainti pakollinen? tyhjennys-sallittu?
                                                   avaimet voi-valita-kartalta? lataa-piirrettaessa-koordinaatit?]} data]
  (let [osoite-alussa @data
        voi-valita-kartalta? (if (some? voi-valita-kartalta?)
                               voi-valita-kartalta?
                               true)
        hae-sijainti (not (nil? sijainti)) ;; sijainti (ilman deref!!) on nil tai atomi. Nil vain jos on unohtunut?
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
                           (when (or
                                   ;; Jos koordinaatteja ei ladata piirtovaiheessa, niin vertaile aina arvon muuttumista
                                   (and (false? lataa-piirrettaessa-koordinaatit?) (not= arvo @alkuperainen-sijainti))
                                   ;; Jos koordinaatit ladataan piirtovaiheessa, näytä karttataso, kun koordinaatit ladattu
                                   (and lataa-piirrettaessa-koordinaatit? arvo (:coordinates arvo)))
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
                 ;; (log "VKM/TR: " (pr-str arvo))
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
          (when (or
                  ;; Lataa koordinaatit jos atomi muuttuu
                  (and (false? lataa-piirrettaessa-koordinaatit?) (not= @vanha-osoite-atom @uusi-osoite-atom))
                  ;; Lataa koordinaatit, jos koordinaatit puuttuvat
                  (and lataa-piirrettaessa-koordinaatit? (nil? (:coordinates @uusi-osoite-atom))))
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

      (fn [{:keys [tyyli lomake? sijainti piste? vaadi-vali? tr-otsikot? vayla-tyyli?
                   pakollinen? disabled? alaotsikot? piilota-nappi?]} data]
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
                                   (and (not vayla-tyyli?) (= tyyli :rivitetty)) 
                                   tierekisterikentat-rivitetty

                                   vayla-tyyli? 
                                   tierekisterikentat-flex
                                   
                                   :else tierekisterikentat-table)

              osoite @data

              [numero alkuosa alkuetaisyys loppuosa loppuetaisyys]
              (map #(when osoite (osoite %)) avaimet)

              muuta! (fn [kentta]
                       #(let [v (-> % .-target .-value)
                              _tr (swap! data assoc kentta (when (and (not (= "" v))
                                                                     (re-matches #"\d*" v))
                                                            (js/parseInt (-> % .-target .-value))))]))
              blur (when hae-sijainti
                     #(tee-tr-haku osoite))
              normalisoi (fn [{:keys [numero alkuosa alkuetaisyys loppuosa loppuetaisyys]}]
                           {numero-avain numero
                            alkuosa-avain alkuosa
                            alkuetaisyys-avain alkuetaisyys
                            loppuosa-avain loppuosa
                            loppuetaisyys-avain loppuetaisyys})
              luokat (if vayla-tyyli? "input-default" "")]
          ;(loki/log "sijainti >" @sijainti avaimet numero alkuosa numero-avain alkuosa-avain)
          [:span {:class (str "tieosoite-kentta "
                              (when @virheet " sisaltaa-virheen")
                              (when vayla-tyyli? " vayla"))}
           (when (and @virheet (false? ala-nayta-virhetta-komponentissa?))
             [:div {:class "virheet"}
              [:div {:class "virhe"}
               [:span (ikonit/livicon-warning-sign) [:span @virheet]]]])

           (let [optiot {:pakollinen? pakollinen?
                         :alaotsikot? alaotsikot?}]
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

              (when (and (not piilota-nappi?) voi-valita-kartalta?)
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

(defn tee-otsikollinen-kentta [{:keys [otsikko kentta-params arvo-atom luokka tyylit
                                       otsikon-luokka otsikon-tag data-muokkaus-fn]}]
  [:span {:class (or luokka "label-ja-kentta")
          :style tyylit}
   [(or otsikon-tag :label) {:class (or otsikon-luokka "kentan-otsikko")} otsikko]
   [:div.kentta
    (if data-muokkaus-fn
      ;; mahdollista 3-arity, joka paremmin Tuck-yhteensopiva. Siihen data-parametri ei saa olla atomi.
      [tee-kentta kentta-params arvo-atom data-muokkaus-fn]
      [tee-kentta kentta-params arvo-atom])]])

(defn tee-otsikko-ja-kentat [{:keys [otsikko luokka kentat otsikon-luokka]}]
  [:span {:class (or luokka "label-ja-kentta")}
   [:label {:class (or otsikon-luokka "kentan-otsikko")}  otsikko]
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

(defmethod tee-kentta :valiotsikko [{:keys [teksti]} data]
  [:div [:h3 teksti]])

(defn raksiboksi
  [{:keys [tiivis? teksti toiminto info-teksti nayta-infoteksti? komponentti disabled?]} checked]
  [:span {:class (merge #{"raksiboksi"} (when tiivis? #{"tiivis"}))}
   [:div.input-group
    [tee-kentta
     {:tyyppi :checkbox
      :teksti teksti
      :disabled? disabled?
      :valitse! toiminto}
     checked]
    (when komponentti
      komponentti)]
   (when nayta-infoteksti?
     info-teksti)])
