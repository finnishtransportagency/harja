(ns harja.ui.lomake
  "Lomakeapureita"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.validointi :as validointi]
            [harja.ui.yleiset :refer [virheen-ohje]]
            [harja.ui.kentat :refer [tee-kentta nayta-arvo atomina]]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.ui.komponentti :as komp]
            [taoensso.truss :as truss :refer-macros [have have! have?]]
            [harja.pvm :as pvm]
            [clojure.string :as str]
            [harja.ui.leijuke :as leijuke]
            [harja.ui.ikonit :as ikonit])
  (:require-macros [harja.makrot :refer [kasittele-virhe]]))

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

(defn rivi
  "Asettaa annetut skeemat vierekkäin samalle riville"
  [& skeemat]
  (->Ryhma nil {:rivi? true} skeemat))

(defn ryhma? [x]
  (instance? Ryhma x))

(defn muokattu?
  "Tarkista onko mitään lomakkeen kenttää muokattu"
  [data]
  (not (empty? (::muokatut data))))

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
          ::skeema))

(defn lomake-lukittu-huomautus
  [nykyinen-lukko]
  [:div.lomake-lukittu-huomautus
   (harja.ui.ikonit/livicon-info-sign) (str " Lomakkeen muokkaaminen on estetty, sillä toinen käyttäjä"
                                            (when (and (:etunimi nykyinen-lukko)
                                                       (:sukunimi nykyinen-lukko))
                                              (str " (" (:etunimi nykyinen-lukko) " " (:sukunimi nykyinen-lukko) ")"))
                                            " muokkaa parhaillaan lomaketta. Yritä hetken kuluttua uudelleen.")])

(defrecord ^:private Otsikko [otsikko])
(defn- otsikko? [x]
  (instance? Otsikko x))

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
          (recur (vec (concat (if (empty? rivi)
                                rivit
                                (conj rivit rivi))
                              [[(->Otsikko (:otsikko s))]
                               (with-meta
                                 (remove nil? (:skeemat s))
                                 {:rivi? true})]))
                 []
                 0
                 skeemat)

          (ryhma? s)
          ;; Muuten lisätään ryhmän otsikko ja jatketaan rivitystä normaalisti
          (recur rivit rivi palstoja
                 (concat [(->Otsikko (:otsikko s))] (remove nil? (:skeemat s)) skeemat))

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

(defn kentan-vihje [{:keys [vihje vihje-leijuke vihje-leijuke-optiot] :as skeema}]
  [:span
   (when vihje
     [kentan-vihje-inline vihje])
   (when vihje-leijuke
     [leijuke/vihjeleijuke vihje-leijuke-optiot vihje-leijuke])])

(defn yleinen-huomautus
  "Yleinen huomautus, joka voidaan näyttää esim. lomakkeen tallennuksen yhteydessä"
  [teksti]
  [:div.lomake-yleinen-huomautus (harja.ui.ikonit/livicon-info-sign) (str " " teksti)])

(defn yleinen-varoitus
  "Yleinen varoitus, joka voidaan näyttää esim. lomakkeen tallennuksen yhteydessä"
  [teksti]
  [:div.lomake-yleinen-varoitus (harja.ui.ikonit/livicon-warning-sign) (str " " teksti)])

(def +piilota-label+ #{:boolean :tierekisteriosoite})

(defn kentan-input
  "Määritellään kentän input"
  [s data muokattava? muokkaa muokkaa-kenttaa-fn aseta-vaikka-sama?]
  (let [{:keys [nimi hae aseta]} s
        hae (or hae #(get % nimi))
        init-arvo (hae data)
        arvo (atom init-arvo)
        seurannan-muuttujat (atom {:vaihda! (muokkaa-kenttaa-fn nimi)
                                   :data data
                                   :s s})]
    (add-watch arvo
               (gensym "input")
               (fn [_ _ vanha-arvo uusi-arvo]
                 ;; Resetoi data, jos uusi data annettu
                 (if (not= uusi-arvo vanha-arvo)
                   (let [{:keys [s data vaihda!]} @seurannan-muuttujat
                         {:keys [aseta nimi]} s]
                     (if aseta
                       (vaihda! (aseta data uusi-arvo))
                       (vaihda! (assoc data nimi uusi-arvo))))
                   (when (and (= uusi-arvo vanha-arvo) aseta-vaikka-sama?)
                     (let [{:keys [s data vaihda!]} @seurannan-muuttujat
                           {:keys [aseta nimi]} s]
                       (if aseta
                         (vaihda! (aseta data uusi-arvo))
                         (vaihda! (assoc data nimi uusi-arvo))))))))
    (fn [{:keys [tyyppi komponentti komponentti-args fmt hae nimi yksikko-kentalle valitse-ainoa? sisallon-leveys?] :as s}
         data muokattava? muokkaa muokkaa-kenttaa-fn]
      (reset! seurannan-muuttujat
              {:vaihda! (muokkaa-kenttaa-fn nimi)
               :data data
               :s s})
      (let [kentta (cond
                     (= tyyppi :komponentti) [:div.komponentti (apply komponentti {:muokkaa-lomaketta (muokkaa s)
                                                                                   :data data} komponentti-args)]
                     (= tyyppi :reagent-komponentti) [:div.komponentti (vec (concat [komponentti {:muokkaa-lomaketta (muokkaa s)
                                                                                                  :data data}]
                                                                                     komponentti-args))]
                     :else (if muokattava?
                             (if (and valitse-ainoa?
                                      (= :valinta tyyppi)
                                      (= 1 (count (or (:valinnat s) ((:valinnat-fn s) data)))))
                               (do (reset! arvo (if-let [hae (:valinta-arvo s)]
                                                  (hae (first (:valinnat s)))
                                                  (first (:valinnat s))))
                                   [:div.form-control-static
                                    ;; :valinta-kentän nayta-arvo käyttää sisäisesti :valinta-nayta optiota
                                    (nayta-arvo s arvo)])

                               (do (have #(contains? % :tyyppi) s)
                                   [tee-kentta (assoc s :lomake? true) arvo]))
                             [:div.form-control-static
                              (if fmt
                                (fmt ((or hae #(get % nimi)) data))
                                (nayta-arvo s arvo))]))
            kentta (if yksikko-kentalle
                     [:div.kentta-ja-yksikko
                      kentta
                      [:span.kentan-yksikko yksikko-kentalle]]
                     kentta)]
        (if sisallon-leveys?
          [:div.kentan-leveys
           kentta]
          kentta)))))

(defn kentta
  "UI yhdelle kentälle, renderöi otsikon ja kentän"
  [{:keys [palstoja nimi otsikko tyyppi col-luokka yksikko pakollinen? sisallon-leveys?
           piilota-label? aseta-vaikka-sama?] :as s}
   data muokkaa-kenttaa-fn muokattava? muokkaa
   muokattu? virheet varoitukset huomautukset]
  [:div.form-group {:class (str (or
                                  ;; salli skeeman ylikirjoittaa ns-avaimella
                                  (::col-luokka s)
                                  col-luokka
                                  (case (or palstoja 1)
                                    1 "col-xs-12 col-sm-6 col-md-5 col-lg-4"
                                    2 "col-xs-12 col-sm-12 col-md-10 col-lg-8"
                                    3 "col-xs-12 col-sm-12 col-md-12 col-lg-12"))
                                (when pakollinen?
                                  " required")
                                (when-not (empty? virheet)
                                  " sisaltaa-virheen")
                                (when-not (empty? varoitukset)
                                  " sisaltaa-varoituksen")
                                (when-not (empty? huomautukset)
                                  " sisaltaa-huomautuksen"))}
   [:div {:class (when sisallon-leveys?
                   "sisallon-leveys lomake-kentan-leveys")}
    (when-not (or (+piilota-label+ tyyppi)
                  piilota-label?)
      [:label.control-label {:for nimi}
       [:span
        [:span.kentan-label otsikko]
        (when yksikko [:span.kentan-yksikko yksikko])]])
    [kentan-input s data muokattava? muokkaa muokkaa-kenttaa-fn aseta-vaikka-sama?]

    (when (and muokattu?
               (not (empty? virheet)))
      [virheen-ohje virheet :virhe])
    (when (and muokattu?
               (not (empty? varoitukset)))
      [virheen-ohje varoitukset :varoitus])
    (when (and muokattu?
               (not (empty? huomautukset)))
      [virheen-ohje huomautukset :huomautus])

    [kentan-vihje s]]])

(def ^:private col-luokat
  ;; PENDING: hyvin vaikea sekä 2 että 3 komponentin määrät saada alignoitua
  ;; bootstrap col-luokilla, pitäisi tehdä siten, että rivi on aina saman
  ;; määrän colleja kuin 2 palstaa ja sen sisällä on jaettu osien width 100%/(count skeemat)
  {2 "col-xs-6 col-md-5 col-lg-3"
   3 "col-xs-3 col-sm-2 col-md-3 col-lg-2"
   4 "col-xs-3"
   5 "col-xs-1"})

(defn nayta-rivi
  "UI yhdelle riville"
  [skeemat data muokkaa-kenttaa-fn voi-muokata? nykyinen-fokus aseta-fokus!
   muokatut virheet varoitukset huomautukset muokkaa]
  (let [rivi? (-> skeemat meta :rivi?)
        col-luokka (when rivi?
                     (col-luokat (count skeemat)))]
    [:div.row.lomakerivi
     (doall
       (for [{:keys [nimi muokattava?] :as s} skeemat
             :let [muokattava? (and voi-muokata?
                                    (or (nil? muokattava?)
                                        (muokattava? data)))]]
         ^{:key nimi}
         [kentta (assoc s
                   :col-luokka col-luokka
                   :focus (= nimi nykyinen-fokus)
                   :on-focus (r/partial aseta-fokus! nimi))
          data muokkaa-kenttaa-fn muokattava? muokkaa
          (get muokatut nimi)
          (get virheet nimi)
          (get varoitukset nimi)
          (get huomautukset nimi)]))]))

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
                      viimeisin ::viimeisin-muokkaus :as tiedot}]
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

  :footer         Komponentti, joka asetetaan lomakkeen footer sijaintiin, yleensä
                  submit nappi tms.

  :footer-fn      vaihtoehto :footer'lle, jolle annetaan footerin muodostava funktio
                  funktiolle annetaan validoitu data parametrina. Parametriin on liitetty
                  avaimet ::virheet, ::varoitukset, ::huomautukset, ja ::puuttuvat-pakolliset-kentat.

  :voi-muokata?   voiko lomaketta muokata, oletuksena true

  Jos :tyyppi:n arvoksi on määritetty :komponentti, niin :komponentti avain ottaa funktion. Tämä funktio ottaa yhden
  parametrin, joka on map avaimilla :muokkaa-lomaketta ja :data.

  :muokkaa-lomaketta    Funktio, joka ottaa lomakkeen data-mapin ja päivittää ::muokatut avaimen skeeman :nimi arvolla
  :data                 validoitu data
  "
  [{:keys [validoi-alussa? validoitavat-avaimet muokkaa! kutsu-muokkaa-renderissa? voi-muokata?]} skeema data]
  (let [fokus (atom nil)
        validoi-avaimet (fn [skeema]
                          (reduce (fn [skeema skeeman-osa]
                                    (conj skeema (select-keys skeeman-osa validoitavat-avaimet)))
                                  [] skeema))
        edellinen-skeema (when validoitavat-avaimet
                           (atom (validoi-avaimet skeema)))]
    (when (and validoi-alussa? voi-muokata?)
      (-> data (validoi skeema) (assoc ::muokatut (into #{} (keep :nimi skeema))) muokkaa!))
    (fn [{:keys [otsikko muokkaa! luokka footer footer-fn virheet varoitukset huomautukset
                 voi-muokata? ei-borderia? validoitavat-avaimet data-cy] :as opts} skeema
         {muokatut ::muokatut
          :as data}]
      (when validoitavat-avaimet
        (let [validoitut-avaimet (validoi-avaimet skeema)]
          (when (not= @edellinen-skeema validoitut-avaimet)
            (reset! edellinen-skeema validoitut-avaimet)
            (muokkaa! (validoi data skeema)))))
      (let [{virheet ::virheet
             varoitukset ::varoitukset
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
                                           muokkaa!)))]
            ;(lovg "RENDER! fokus = " (pr-str @fokus))
            [:div
             (merge
               {:class (str "lomake " (when ei-borderia? "lomake-ilman-borderia")
                            luokka)}
               (when data-cy
                 {:data-cy data-cy}))
             (when otsikko
               [:h3.lomake-otsikko otsikko])
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
                                  #(muokkaa-kenttaa-fn (:nimi %))]]
                     (if otsikko
                       ^{:key i}
                       [:span
                        [:h3.lomake-ryhman-otsikko (:otsikko otsikko)]
                        rivi-ui]
                       (with-meta rivi-ui {:key i}))))
                 (rivita skeema)))

             (when-let [footer (if footer-fn
                                 (footer-fn (assoc validoitu-data
                                              ::skeema skeema))
                                 footer)]
               [:div.lomake-footer.row
                [:div.col-md-12 footer]])]))))))

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
       (str/capitalize (numero lkm))
       (if (= 1 lkm)
         " pakollinen kenttä "
         " pakollista kenttää ")
       "puuttuu: "

       (str/join ", "
                 (let [skeema (pura-ryhmat skeema)]
                   (for [puuttuva-nimi puuttuvat
                         :let [{:keys [otsikko] :as s}
                               (first (filter #(= puuttuva-nimi (:nimi %)) skeema))]]
                     otsikko)))])))
