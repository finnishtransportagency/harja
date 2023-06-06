(ns harja.ui.lomake
  "Lomakeapureita"
  (:require [goog.string :as gstring]
            [clojure.spec.alpha :as s]
            [reagent.core :refer [atom] :as r]
            [harja.ui.validointi :as validointi]
            [harja.ui.yleiset :refer [virheen-ohje]]
            [harja.ui.kentat :refer [tee-kentta nayta-arvo atomina]]
            [harja.loki :refer [log logt tarkkaile!] :as loki]
            [harja.ui.komponentti :as komp]
            [taoensso.truss :as truss :refer-macros [have have! have?]]
            [harja.pvm :as pvm]
            [clojure.string :as string]
            [harja.ui.leijuke :as leijuke]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.napit :as napit])
  (:require-macros [harja.makrot :refer [kasittele-virhe]]))

(defrecord ^:private Otsikko [otsikko optiot])
(defn- otsikko? [x]
  (instance? Otsikko x))

; kokoelma palstoja jota käytetään lomakkeen muodostamiseen
(defrecord Palstat [optiot skeemat])

; yksittäinen palsta, joka piirretään
(defrecord Palsta ^:private [optiot skeemat])

(defn- palsta? [x]
  (instance? Palsta x))

(defrecord Ryhma [otsikko optiot skeemat])

(defn ryhma [otsikko-tai-optiot & skeemat]
  (if-let [optiot (and (map? otsikko-tai-optiot)
                       otsikko-tai-optiot)]
    (->Ryhma (:otsikko optiot)
             (merge {:ulkoasu :oletus}
                    optiot)
             skeemat)
    (->Ryhma otsikko-tai-optiot
             {:ulkoasu :oletus} skeemat)))

(defn palstat
  "Asetetaan annetut skeemat samaan vertikaaliseen palstaan
  Flex-optiolla voidaan tehdä käärivästä luokasta flex, kts. luokka lomakepalsta-flex,
  ja kentille voidaan passata ::col/luokka-arvoksi kokonainen tai puolikas, jolla voidaan säädellä rivitystä."
  [{:keys [lukumaara puolikas flex?]} & palstan-optiot-ja-skeemat]
  (->Palstat {:lukumaara lukumaara
              :puolikas puolikas
              :flex? flex?} (remove nil? palstan-optiot-ja-skeemat)))

(defn palstoja? [x]
  (instance? Palstat x))

(defn- optiot? [m]
  (contains? m ::rivi-optiot))

(s/def ::rivi-optioilla (s/cat :optiot (s/? optiot?) :skeemat (s/* map?)))

#_(defn rivi
  "Asettaa annetut skeemat vierekkäin samalle riville"
   ;; Kommentoitu toistaiseksi pois, koska aiheuttaa joidenkin käyttöliittymäelementtien katoamisen (VHAR-5129)
  [& skeemat]
  (let [{{::keys [rivi-optiot]} :optiot skeemat :skeemat} (s/conform ::rivi-optioilla skeemat)]
    (->Ryhma nil (merge {:rivi? true} rivi-optiot) skeemat)))

(defn rivi
  "Asettaa annetut skeemat vierekkäin samalle riville"
  [& skeemat]
    (->Ryhma nil {:rivi? true} skeemat))

(defn ryhma? [x]
  (instance? Ryhma x))

(defn- pura-ryhmat
  "Purkaa skeemat ryhmistä yhdeksi flat listaksi, jossa ei ole nil arvoja.
Ryhmien otsikot lisätään väliin Otsikko record tyyppinä."
  [skeemat]
  (loop [acc []
         [s & skeemat] (remove nil? skeemat)]
    (if-not s
      acc
      (cond
        (otsikko? s)
        (recur acc skeemat)

        (ryhma? s)
        (recur acc
               (concat (remove nil? (:skeemat s)) skeemat))

        :default
        (recur (conj acc s)
               skeemat)))))

(defn muokattu?
  "Tarkista onko mitään lomakkeen kenttää muokattu"
  [data]
  (not (empty? (::muokatut data))))

(defn puuttuvien-pakollisten-kenttien-otsikot
  "Palauttaa setin pakollisia kenttiä, jotka puuttuvat"
  [data]
  (map (fn [kentta]
         (:otsikko (first (filter #(= kentta (:nimi %))
                                  (pura-ryhmat (::skeema data))))))
       (::puuttuvat-pakolliset-kentat data)))

(defn puuttuvat-pakolliset-kentat
  "Palauttaa setin pakollisia kenttiä, jotka puuttuvat"
  [data]
  (::puuttuvat-pakolliset-kentat data))

(defn pakollisia-kenttia-puuttuu? [data]
  (not (empty? (puuttuvat-pakolliset-kentat data))))

(defn virheita?
  "Tarkistaa onko lomakkeella validointivirheitä"
  [data]
  (not (empty? (::virheet data))))

(defn validi?
  "Tarkista onko lomake validi, palauttaa true jos lomakkeella ei ole validointivirheitä
ja kaikki pakolliset kentät on täytetty"
  [data]
  (and (not (virheita? data))
       (not (pakollisia-kenttia-puuttuu? data))))

(defn voi-tallentaa-ja-muokattu?
  "Tarkista voiko lomakkeen tallentaa ja onko sitä muokattu"
  [data]
  (and (muokattu? data)
       (validi? data)))

(defn voi-tallentaa?
  "Tarkista onko lomakkeen tallennus sallittu"
  [data]
  (validi? data))

(defn lomaketiedot
  "Lomakkeen ohjaustiedot"
  [data]
  (select-keys data [::muokatut
                     ::virheet
                     ::varoitukset
                     ::huomautukset
                     ::puuttuvat-pakolliset-kentat
                     ::ensimmainen-muokkaus
                     ::viimeisin-muokkaus
                     ::viimeksi-muokattu-kentta
                     ::skeema]))

;;TODO siirry käyttämään lomakkeen-muokkaus ns:ssa tätä
(defn ilman-lomaketietoja
  "Palauttaa lomakkeen datan ilman lomakkeen ohjaustietoja"
  [data]
  (dissoc data
          ::muokatut
          ::virheet
          ::varoitukset
          ::huomautukset
          ::puuttuvat-pakolliset-kentat
          ::ensimmainen-muokkaus
          ::viimeisin-muokkaus
          ::viimeksi-muokattu-kentta
          ::skeema))

(defn lomake-lukittu-huomautus
  [nykyinen-lukko]
  [:div.lomake-lukittu-huomautus
   (harja.ui.ikonit/livicon-info-sign) (str " Lomakkeen muokkaaminen on estetty, sillä toinen käyttäjä"
                                            (when (and (:etunimi nykyinen-lukko)
                                                       (:sukunimi nykyinen-lukko))
                                              (str " (" (:etunimi nykyinen-lukko) " " (:sukunimi nykyinen-lukko) ")"))
                                            " muokkaa parhaillaan lomaketta. Yritä hetken kuluttua uudelleen.")])

(defn- palstoita
  "Ottaa sisään ryhmiä ja muodostaa jokaisesta ryhmästä palstan vertikaalisesti"
  [rivit {:keys [puolikas]} skeemat]
  (let [lukumaara (/ (count skeemat) 2)
        partitioidut-skeemat (partition 2 (remove nil? skeemat))]
    (conj rivit (with-meta
                  (vec (map (fn [[optiot palstan-skeemat]]
                              (->Palsta (assoc optiot :lukumaara lukumaara) palstan-skeemat))
                            partitioidut-skeemat))
                  {:palsta? true
                   :rivi?   true}))))

(defn- rivita
  "Rivittää kentät siten, että kaikki palstat tulee täyteen.
  Uusi rivi alkaa kun palstat ovat täynnä, :uusi-rivi? true on annettu tai tulee uusi ryhmän otsikko."
  [skeemat]
  (loop [rivit []
         rivi []
         palstoja 0
         [s & skeemat] (remove nil? skeemat)]
    (if-not s
      (if-not (empty? rivi)
        (conj rivit rivi)
        rivit)
      (let [kentan-palstat (or (:palstoja s) 1)]
        (cond
          (and (ryhma? s) (:rivi? (:optiot s)))
          ;; Jos kyseessä on ryhmä, joka haluataan samalle riville, lisätään
          ;; ryhmän skeemat suoraan omana rivinään riveihin
          (do
            (recur (vec (concat (if (empty? rivi)
                                    rivit
                                    (conj rivit rivi))
                                  [[(->Otsikko (:otsikko s) (:optiot s))]
                                   (with-meta
                                     (remove nil? (:skeemat s))
                                     (merge {} (:optiot s) {:rivi? true}))]))
                     []
                     0
                     skeemat))

          ;; Jos palstoja, palstat alkavat tyhjältä riviltä, joten lisätään työn alla ollut rivi settiin
          ;; ja luodaan palstat, jotka tulevat omalle rivilleen.
          (palstoja? s)
          (recur (palstoita (if (not (empty? rivi))
                              (conj rivit rivi)
                              rivit)
                            (:optiot s)
                            (:skeemat s))
                 []
                 0
                 skeemat)

          (ryhma? s)
          ;; Muuten lisätään ryhmän otsikko ja jatketaan rivitystä normaalisti
          (recur rivit rivi palstoja
                 (concat
                  [(->Otsikko (:otsikko s) (:optiot s))]
                  (remove nil? (:skeemat s))
                  skeemat))

          :default
          ;; Rivitä skeema
          (if (or (otsikko? s)
                  (:uusi-rivi? s)
                  (> (+ palstoja kentan-palstat) 2))
            (recur (if (empty? rivi)
                     rivit
                     (conj rivit rivi))
                   [s]
                   (if (otsikko? s) 0 kentan-palstat)
                   skeemat)
            ;; Mahtuu tälle riville, lisätään nykyiseen riviin
            (recur rivit
                   (conj rivi s)
                   (+ palstoja kentan-palstat)
                   skeemat)))))))

(defn kentan-vihje-inline [vihje]
  (let [vihjeet (if (vector? vihje) vihje [vihje])]
    [:div {:class
           (str "inline-block yleinen-pikkuvihje")}
     [:div.vihjeen-sisalto
      (harja.ui.ikonit/livicon-info-sign)
      [:span (str " " (first vihjeet))]
      (map-indexed
        (fn [i vihje]
          ^{:key i}
          [:div.vihjeen-lisarivi (str "  " vihje)])
        (rest vihjeet))]]))

(defn checkboxin-vihje [vihje]
  (let [vihjeet (if (vector? vihje) vihje [vihje])]
    [:div.checkboxin-vihje.caption
     (map-indexed
       (fn [i vihje]
         ^{:key (str "vihje-" i)}
         [:div vihje]) vihjeet)]))

(defn kentan-vihje [{:keys [vihje vihje-leijuke vihje-leijuke-optiot tyyppi] :as skeema}]
  [:span
   (when vihje
     (if (or (= :checkbox tyyppi) (= :checkbox-group tyyppi))
       [checkboxin-vihje vihje]
       [kentan-vihje-inline vihje]))
   (when vihje-leijuke
     [leijuke/vihjeleijuke vihje-leijuke-optiot vihje-leijuke])])

(defn yleinen-varoitus
  "Yleinen varoitus, joka voidaan näyttää esim. lomakkeen tallennuksen yhteydessä"
  [teksti]
  [:div.lomake-yleinen-varoitus (harja.ui.ikonit/livicon-warning-sign) (str " " teksti)])

(def +piilota-label+ #{:boolean :tierekisteriosoite})

(defn kentan-input
  "Määritellään kentän input"
  [s data muokattava? muokkaa muokkaa-kenttaa-fn aseta-vaikka-sama? {:keys [vayla-tyyli?] :as kentta-opts}]
  (let [{:keys [nimi hae aseta kentan-arvon-luokka]} s
        hae (or hae #(if (vector? nimi)
                       (get-in % nimi)
                       (get % nimi)))
        init-arvo (atom (hae data))
        arvo (atom (hae data))
        seurannan-muuttujat (atom {:vaihda! (muokkaa-kenttaa-fn nimi)
                                   :data    data
                                   :s       s})]
    (add-watch arvo
               (gensym "input")
               (fn [_ _ vanha-arvo uusi-arvo]
                 ;; Resetoi data, jos uusi data annettu
                 (if (not= uusi-arvo vanha-arvo)
                   (let [{:keys [s data vaihda!]} @seurannan-muuttujat
                         {:keys [aseta nimi]} s
                         assoc-fn (if (vector? nimi)
                                    assoc-in
                                    assoc)]
                     (if aseta
                       (vaihda! (aseta data uusi-arvo))
                       (vaihda! (assoc-fn data nimi uusi-arvo))))
                   (when (and (= uusi-arvo vanha-arvo) aseta-vaikka-sama?)
                     (let [{:keys [s data vaihda!]} @seurannan-muuttujat
                           {:keys [aseta nimi]} s
                           assoc-fn (if (vector? nimi)
                                      assoc-in
                                      assoc)]
                       (if aseta
                         (vaihda! (aseta data uusi-arvo))
                         (vaihda! (assoc-fn data nimi uusi-arvo))))))))
    (fn [{:keys [tyyppi komponentti komponentti-args fmt hae nimi kentan-arvon-luokka
                 yksikko-kentalle valitse-ainoa? sisallon-leveys?] :as s}
         data muokattava? muokkaa muokkaa-kenttaa-fn _ opts]
      (reset! seurannan-muuttujat
              {:vaihda! (muokkaa-kenttaa-fn nimi)
               :data    data
               :s       s})
      ; jos ulkopuolelta data on päivitetty esim. rajapinnasta saadun päivitetyn tiedon myötä, pitää resetoida arvo ja tila
      (when (:tarkkaile-ulkopuolisia-muutoksia? opts)
        (let [data-arvo (cond
                          hae
                          (hae data)

                          (vector? nimi)
                          (get-in data nimi)

                          :else
                          (get data nimi))]
          (when (not= @arvo
                      data-arvo)
            (do
              (loki/log "data on muuttunut ulkopuolisesta lähteestä" @arvo "->" data-arvo)
              (reset! init-arvo data-arvo)
              (reset! arvo data-arvo)))))
      (let [kentta (cond
                     (= tyyppi :komponentti) [:div.komponentti (apply komponentti {:muokkaa-lomaketta (muokkaa s)
                                                                                   :data              data} komponentti-args)]
                     (= tyyppi :reagent-komponentti) [:div.komponentti (vec (concat [komponentti {:muokkaa-lomaketta (muokkaa s)
                                                                                                  :data              data}]
                                                                                    komponentti-args))]
                     :else (if muokattava?
                             (if (and valitse-ainoa?
                                      (= :valinta tyyppi)
                                      (= 1 (count (or (:valinnat s) ((:valinnat-fn s) data)))))
                               (do (reset! arvo (if-let [hae (:valinta-arvo s)]
                                                  (hae (first (:valinnat s)))
                                                  (first (:valinnat s))))
                                   [:div {:class (str "form-control-static lomake-arvo")}
                                    ;; :valinta-kentän nayta-arvo käyttää sisäisesti :valinta-nayta optiota
                                    (nayta-arvo s arvo)])

                               (do (have #(contains? % :tyyppi) s)
                                   [tee-kentta (merge opts (assoc s :lomake? true)) arvo]))
                             [:div {:class (str "form-control-static lomake-arvo " kentan-arvon-luokka)}
                              (if fmt
                                (fmt ((or hae #(let [get-fn (if (vector? nimi)
                                                              get-in
                                                              get)]
                                                 (get-fn % nimi))) data))
                                (nayta-arvo s arvo))]))
            kentta (if yksikko-kentalle
                     [:div.kentta-ja-yksikko
                      kentta
                      [:span.kentan-yksikko yksikko-kentalle]]
                     kentta)]
        (cond
          (and sisallon-leveys?
               (:label-ja-kentta-samalle-riville? opts))
          [:div.basis256
           [:div.kentan-leveys kentta]]

          sisallon-leveys?
          [:div.kentan-leveys
           kentta]

          (:label-ja-kentta-samalle-riville? opts)
          [:div.basis256 kentta]
          :else
          kentta)))))

(defn kentta
  "UI yhdelle kentälle, renderöi otsikon ja kentän"
  [{:keys [palstoja nimi otsikko tyyppi col-luokka yksikko pakollinen? sisallon-leveys? label-ja-kentta-samalle-riville?
           piilota-label? aseta-vaikka-sama? tarkkaile-ulkopuolisia-muutoksia? kaariva-luokka piilota-yksikko-otsikossa? tyhja-otsikko? virhe-optiot] :as s}
   data muokkaa-kenttaa-fn muokattava? muokkaa
   muokattu? virheet varoitukset huomautukset {:keys [vayla-tyyli? voi-muokata?] :as opts}]
  [:div.form-group {:class (str (or
                                  ;; salli skeeman ylikirjoittaa ns-avaimella
                                  (::col-luokka s)
                                  col-luokka
                                  (case (or palstoja 1)
                                    1 "col-xs-12 col-sm-6 col-md-5 col-lg-4"
                                    2 "col-xs-12 col-sm-12 col-md-10 col-lg-8"
                                    3 "col-xs-12 col-sm-12 col-md-12 col-lg-12"))
                                (when (and pakollinen? muokattava?)
                                  " required")
                                (when-not (empty? virheet)
                                  " sisaltaa-virheen")
                                (when-not (empty? varoitukset)
                                  " sisaltaa-varoituksen")
                                (when-not (empty? huomautukset)
                                  " sisaltaa-huomautuksen"))}
   [:div {:class (str
                   (when sisallon-leveys?
                     "sisallon-leveys lomake-kentan-leveys ")
                   (when kaariva-luokka kaariva-luokka)
                   (when label-ja-kentta-samalle-riville? "flex-row "))}
    (when-not (or (+piilota-label+ tyyppi)
                  piilota-label?)
      [:label.control-label
       (merge {:for nimi} (when label-ja-kentta-samalle-riville? {:class "basis256"}))
       [:span
        [:span.kentan-label
         (if tyhja-otsikko?
           (gstring/unescapeEntities "&nbsp;")
           otsikko)]
        (when (and yksikko (not piilota-yksikko-otsikossa?)) [:span.kentan-yksikko yksikko])]])
    [kentan-input s data muokattava? muokkaa muokkaa-kenttaa-fn aseta-vaikka-sama?
     (as-> opts opts
           (assoc opts :tarkkaile-ulkopuolisia-muutoksia? tarkkaile-ulkopuolisia-muutoksia?)
           (assoc opts :label-ja-kentta-samalle-riville? label-ja-kentta-samalle-riville?)
           (assoc opts :muokattu? muokattu?)
           (if (not (empty? virheet)) (assoc opts :virhe? true) opts))]

    (when (and muokattu?
               (not (empty? virheet)))
      [virheen-ohje virheet :virhe virhe-optiot])
    (when (:virheteksti s)
      [virheen-ohje (if-not (vector? (:virheteksti s))
                      (conj [] (:virheteksti s))
                      (:virheteksti s)) :virhe virhe-optiot])
    (when (and muokattu?
               (not (empty? varoitukset)))
      [virheen-ohje varoitukset :varoitus virhe-optiot])
    (when (and muokattu?
               (not (empty? huomautukset)))
      [virheen-ohje huomautukset :huomautus virhe-optiot])

    [kentan-vihje s]]])

(def ^:private col-luokat
  ;; PENDING: hyvin vaikea sekä 2 että 3 komponentin määrät saada alignoitua
  ;; bootstrap col-luokilla, pitäisi tehdä siten, että rivi on aina saman
  ;; määrän colleja kuin 2 palstaa ja sen sisällä on jaettu osien width 100%/(count skeemat)
  {2 "col-xs-6 col-md-5 col-lg-3"
   3 "col-xs-3 col-sm-2 col-md-3"
   4 "col-xs-3"
   5 "col-xs-2"})

(defn- nayta-palsta [palsta {:keys [voi-muokata? data aseta-fokus! nykyinen-fokus
                                    muokkaa muokkaa-kenttaa-fn
                                    varoitukset muokatut virheet huomautukset rivi-opts]}]
  [:div {:key (str "div-nayta-palsta" (first (:skeemat palsta)))
         :class ["lomakepalsta" (when (-> palsta :optiot :puolikas) "puolikas")
                 (when (-> palsta :optiot :flex?) "lomakepalsta-flex")]}
   (when (-> palsta :optiot :otsikko)
     [:h3 (-> palsta :optiot :otsikko)])
   (for [{:keys [nimi muokattava?] :as p} (remove nil? (:skeemat palsta))]
     (let [muokattava? (and voi-muokata?
                            (or (nil? muokattava?)
                                (muokattava? data)))]
       ^{:key (str "palsta-kentta-" nimi)}
       [kentta (assoc p
                 :focus (= nimi nykyinen-fokus)
                 :on-focus (r/partial aseta-fokus! nimi))
        data muokkaa-kenttaa-fn muokattava? muokkaa
        (get muokatut nimi)
        (get virheet nimi)
        (get varoitukset nimi)
        (get huomautukset nimi)
        rivi-opts]))])

(defn nayta-rivi
  "UI yhdelle riville"
  [skeemat data muokkaa-kenttaa-fn voi-muokata? nykyinen-fokus aseta-fokus!
   muokatut virheet varoitukset huomautukset muokkaa {:keys [vayla-tyyli? tarkkaile-ulkopuolisia-muutoksia? virhe-optiot on-blur] :as rivi-opts}]
  (let [rivi? (-> skeemat meta :rivi?)
        {:keys [luokat]} (-> skeemat meta)
        palstoitettu? (-> skeemat meta :palsta?)
        col-luokka (when rivi?
                     (col-luokat (count skeemat)))
        ]
    [:div
     {:class (cond-> []
               (some? luokat)
               (concat luokat)

               (and (empty? luokat) palstoitettu?)
               (conj "lomakepalstat"
                 (:rivi-luokka (first (:skeemat (first skeemat)))))


               (empty? luokat)
               (conj "row" (:rivi-luokka (first skeemat))))}
     (doall
       (for [{:keys [nimi muokattava?] :as s} skeemat
             :let [muokattava? (and voi-muokata?
                                    (or (nil? muokattava?)
                                        (muokattava? data)))]]
         (if (palsta? s)
           (nayta-palsta s {:voi-muokata?       voi-muokata?
                            :data               data
                            :aseta-fokus!       aseta-fokus!
                            :nykyinen-fokus     nykyinen-fokus
                            :muokkaa            muokkaa
                            :muokkaa-kenttaa-fn muokkaa-kenttaa-fn
                            :muokatut           muokatut
                            :varoitukset        varoitukset
                            :virheet            virheet
                            :huomautukset       huomautukset
                            :rivi-opts          rivi-opts})
           ^{:key (str "rivi-kentta-" nimi)}
           [kentta
            (cond-> s
              true (assoc
                    :col-luokka col-luokka
                    :focus (= nimi nykyinen-fokus)
                    :on-focus (r/partial aseta-fokus! nimi))
              virhe-optiot (assoc :virhe-optiot virhe-optiot)
              tarkkaile-ulkopuolisia-muutoksia? (assoc
                                                 :tarkkaile-ulkopuolisia-muutoksia? tarkkaile-ulkopuolisia-muutoksia?))
            data muokkaa-kenttaa-fn muokattava? muokkaa
            (get muokatut nimi)
            (get virheet nimi)
            (get varoitukset nimi)
            (get huomautukset nimi)
            (merge rivi-opts (when (some? on-blur) {:on-blur (r/partial on-blur nimi)}))])))]))

(defn validoi [tiedot skeema]
  (let [kaikki-skeemat (pura-ryhmat skeema)
        kaikki-virheet (validointi/validoi-rivin-kentat nil tiedot kaikki-skeemat :validoi)
        kaikki-varoitukset (validointi/validoi-rivin-kentat nil tiedot kaikki-skeemat :varoita)
        kaikki-huomautukset (validointi/validoi-rivin-kentat nil tiedot kaikki-skeemat :huomauta)
        puuttuvat-pakolliset-kentat (into #{}
                                          (map :nimi)
                                          (validointi/puuttuvat-pakolliset-kentat tiedot
                                                                                  kaikki-skeemat))]
    (assoc tiedot
      ::virheet kaikki-virheet
      ::varoitukset kaikki-varoitukset
      ::huomautukset kaikki-huomautukset
      ::puuttuvat-pakolliset-kentat puuttuvat-pakolliset-kentat)))

(defn- muokkausaika [{ensimmainen ::ensimmainen-muokkaus
                      viimeisin   ::viimeisin-muokkaus :as tiedot}]
  (assoc tiedot
    ::ensimmainen-muokkaus (or ensimmainen (pvm/nyt))
    ::viimeisin-muokkaus (pvm/nyt)))

(defn lomake
  "Geneerinen lomakekomponentti, joka käyttää samaa kenttien määrittelymuotoa kuin grid.
  Ottaa kolme parametria: optiot, skeeman (vektori kenttiä) sekä datan (mäppi).

  Kenttiä voi poistaa käytöstä ehdollisesti, kaikki nil kentät jätetään näyttämättä.
  Kenttä voi olla myös ryhmä (ks. ryhma funktio), jolla on otsikko ja kentät.
  Ryhmille annetaan oma väliotsikko lomakkeelle.

  Optioissa voi olla seuraavat avaimet:

  :muokkaa!       callback, jolla kaikki muutokset tehdään, ottaa sisään uudet tiedot
                  ja tekee sille jotain (oletettavasti swap! tai reset! atomille,
                  joka sisältää lomakkeen tiedot)

  :header         Komponentti, joka asetetaan lomakkeen header sijaintiin, ennen otsikkoa

  :header-fn      Sama kuin footer-fn, mutta headerille

  :footer         Komponentti, joka asetetaan lomakkeen footer sijaintiin, yleensä
                  submit nappi tms.

  :footer-fn      vaihtoehto :footer'lle, jolle annetaan footerin muodostava funktio
                  funktiolle annetaan validoitu data parametrina. Parametriin on liitetty
                  avaimet ::virheet, ::varoitukset, ::huomautukset, ja ::puuttuvat-pakolliset-kentat.
                  
  :footer-luokka  Mahdollinen luokka footerille, joka ylikirjoittaa vakion col-md-12.

  :voi-muokata?   voiko lomaketta muokata, oletuksena true

  Jos :tyyppi:n arvoksi on määritetty :komponentti, niin :komponentti avain ottaa funktion. Tämä funktio ottaa yhden
  parametrin, joka on map avaimilla :muokkaa-lomaketta ja :data.

  :muokkaa-lomaketta    Funktio, joka ottaa lomakkeen data-mapin ja päivittää ::muokatut avaimen skeeman :nimi arvolla
  :data                 validoitu data
  "
  [{:keys [validoi-alussa? validoitavat-avaimet muokkaa! kutsu-muokkaa-renderissa? voi-muokata? sulje-fn]} skeema data]
  (let [fokus (atom nil)
        validoi-avaimet (fn [skeema]
                          (reduce (fn [skeema skeeman-osa]
                                    (conj skeema (select-keys skeeman-osa validoitavat-avaimet)))
                                  [] skeema))
        edellinen-skeema (when validoitavat-avaimet
                           (atom (validoi-avaimet skeema)))]
    (when (and validoi-alussa? voi-muokata?)
      (-> data (validoi skeema) (assoc ::muokatut (into #{} (keep :nimi skeema))) muokkaa!))
    (fn [{:keys [otsikko otsikko-elementti otsikko-komp muokkaa! luokka footer footer-fn virheet varoitukset huomautukset header header-fn
                 voi-muokata? ei-borderia? validoitavat-avaimet data-cy vayla-tyyli? palstoita? footer-luokka
                 tarkkaile-ulkopuolisia-muutoksia? overlay ryhman-luokka virhe-optiot blurrissa!] :as opts} skeema
         {muokatut ::muokatut
          :as      data}]
      (when validoitavat-avaimet
        (let [validoitut-avaimet (validoi-avaimet skeema)]
          (when (not= @edellinen-skeema validoitut-avaimet)
            (reset! edellinen-skeema validoitut-avaimet)
            (muokkaa! (validoi data skeema)))))
      (let [{virheet      ::virheet
             varoitukset  ::varoitukset
             huomautukset ::huomautukset :as validoitu-data} (validoi data skeema)]
        (when (and kutsu-muokkaa-renderissa?
                   (or (not= virheet (::virheet data))
                       (not= varoitukset (::varoitukset data))
                       (not= huomautukset (::huomautukset data))))
          (muokkaa! validoitu-data))
        (kasittele-virhe
          (let [voi-muokata? (if (some? voi-muokata?)
                               voi-muokata?
                               true)
                muokkaa-kenttaa-fn (fn [nimi]
                                     (fn [uudet-tiedot]
                                       (assert muokkaa! (str ":muokkaa! puuttuu, opts:" (pr-str opts)))
                                       (-> uudet-tiedot
                                           muokkausaika
                                           (validoi skeema)
                                           (assoc ::muokatut (conj (or (::muokatut uudet-tiedot)
                                                                       #{}) nimi))
                                           (assoc ::viimeksi-muokattu-kentta nimi)
                                           muokkaa!)))]
            ;(lovg "RENDER! fokus = " (pr-str @fokus))
            [:div
             (merge
               {:class (str "lomake " (when ei-borderia? "ei-borderia ")
                            (when-not voi-muokata? "lukutila ")
                            luokka)}
               (when overlay {:style {:width (or (:leveys overlay) "400px")}})
               (when data-cy
                 {:data-cy data-cy}))
             (when-let [header (if header-fn
                                 (header-fn (assoc validoitu-data
                                              ::skeema skeema))
                                 header)]
               [:div.row header])
             (when sulje-fn [napit/sulje-ruksi sulje-fn])
             (when otsikko
               [(or otsikko-elementti :h3) {:class "lomake-otsikko"} otsikko])
             (when otsikko-komp
               [otsikko-komp (assoc validoitu-data ::skeema skeema)])
             (doall
               (map-indexed
                 (fn [i skeemat]
                   (let [otsikko (when (otsikko? (first skeemat))
                                   (first skeemat))
                         skeemat (if otsikko
                                   (rest skeemat)
                                   skeemat)
                         rivi-ui [nayta-rivi skeemat
                                  validoitu-data
                                  muokkaa-kenttaa-fn
                                  voi-muokata?
                                  @fokus
                                  (r/partial reset! fokus)
                                  muokatut
                                  virheet
                                  varoitukset
                                  huomautukset
                                  #(muokkaa-kenttaa-fn (:nimi %))
                                  (merge
                                   {:vayla-tyyli? vayla-tyyli?
                                    :virhe-optiot virhe-optiot
                                    :tarkkaile-ulkopuolisia-muutoksia? tarkkaile-ulkopuolisia-muutoksia?}
                                   (when (some? blurrissa!) {:on-blur blurrissa!}))]]
                     (if otsikko
                       ^{:key (str otsikko "-" i)}
                       [:div {:class (get-in otsikko [:optiot :ryhman-luokka])}
                        (if-let [nappi (get-in otsikko [:optiot :nappi])]
                          [:div.lomake-ryhman-otsikko.napilla
                           [:h4 (:otsikko otsikko)]
                           nappi]
                          [:h4.lomake-ryhman-otsikko (:otsikko otsikko)])
                        rivi-ui]
                       ^{:key (str "rivi-ui-with-meta-" i)}
                       (with-meta rivi-ui {:key (str "rivi-ui-" i)}))))
                 (rivita skeema)))

             (when-let [footer (if footer-fn
                                 (footer-fn (assoc validoitu-data
                                              ::skeema skeema))
                                 footer)]
               [:div.lomake-footer.row
                [:div
                 {:class (or footer-luokka "col-md-12")} footer]])]))))))

(defn numero
  "Näyttää numeron tekstinä, yli kymmenen numeroina alle kymmenen sanoina."
  [n]
  (if (> n 10)
    (str n)
    (case n
      0 "nolla"
      1 "yksi"
      2 "kaksi"
      3 "kolme"
      4 "neljä"
      5 "viisi"
      6 "kuusi"
      7 "seitsemän"
      8 "kahdeksan"
      9 "yhdeksän"
      10 "kymmenen")))

(defn nayta-puuttuvat-pakolliset-kentat [{skeema ::skeema puuttuvat ::puuttuvat-pakolliset-kentat}]
  (let [lkm (count puuttuvat)]
    (if (zero? lkm)
      [:span]
      [:span.puuttuvat-pakolliset-kentat
       (string/capitalize (numero lkm))
       (if (= 1 lkm)
         " pakollinen kenttä "
         " pakollista kenttää ")
       "puuttuu: "

       (string/join ", "
                 (let [skeema (pura-ryhmat skeema)]
                   (for [puuttuva-nimi puuttuvat
                         :let [{:keys [otsikko] :as s}
                               (first (filter #(= puuttuva-nimi (:nimi %)) skeema))]]
                     otsikko)))])))

(defn lomake-spacer [{:keys [palstoja]}]
  {:nimi ::spacer :piilota-label? true :tyyppi :komponentti :palstoja (or palstoja 3)
   :komponentti (fn [rivi] [:hr])})
