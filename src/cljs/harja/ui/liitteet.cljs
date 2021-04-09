(ns harja.ui.liitteet
  "Yleisiä UI-komponentteja liitteiden lataamisen hoitamiseksi."
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<! >! timeout]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.modal :as modal]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.ikonit :as ikonit]
            [harja.domain.oikeudet :as oikeudet]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.img-with-exif :refer [img-with-exif]]
            [harja.fmt :as fmt]
            [harja.ui.komponentti :as komp]
            [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]
            [harja.ui.viesti :as viesti])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn poista-liite-kannasta-kysely
  [{:keys [urakka-id domain domain-id liite-id] :as args}]
  (assert (and urakka-id domain domain-id liite-id) (pr-str "Puutteelit argumentit, sain: " args))
  (k/post! :poista-liite-linkki {:urakka-id urakka-id
                                 :domain domain
                                 :liite-id liite-id
                                 :domain-id domain-id}))

(defn poista-liite-kannasta
  [{:keys [urakka-id domain domain-id liite-id poistettu-fn] :as args}]
  (assert (and urakka-id domain domain-id liite-id poistettu-fn) (pr-str "Puutteelit argumentit, sain: " args))
  (go
    (let [vastaus (<! (poista-liite-kannasta-kysely
                        {:urakka-id urakka-id
                         :domain domain
                         :domain-id domain-id
                         :liite-id liite-id}))]
      (if (k/virhe? vastaus)
        (viesti/nayta! "Liitteen poisto epäonnistui!" :danger)
        (do (poistettu-fn)
            (viesti/nayta! "Liite poistettu!" :success))))))

(defn lyhenna-pitkan-liitteen-nimi [nimi]
  (if (> (count nimi) 20)
    (str (subs nimi 0 20) "...")
    nimi))

(defn naytettava-liite?
  "Kertoo, voidaanko liite näyttää käyttäjälle modaalissa (esim. kuvat)
   vai onko tarkoitus tarjota puhdas latauslinkki"
  [liite]
  (if liite
    (zero? (.indexOf (:tyyppi liite) "image/"))
    false))

(defn liitekuva-modalissa [liite]
  [img-with-exif {:class "kuva-modalissa"
                  :src (k/liite-url (:id liite))}])

(defn- nayta-liite-modalissa [liite]
  (modal/nayta!
    {:otsikko (str "Liite: " (:nimi liite))
     :leveys "80%"
     :luokka "kuva-modal"}
    (liitekuva-modalissa liite)))

(defn liitteen-poisto [liite poista-fn]
  [:span
   {:style {:padding-left "5px"}
    :on-click #(do
                 (.stopPropagation %)
                 (varmista-kayttajalta/varmista-kayttajalta
                   {:otsikko "Liitteen poistaminen"
                    :sisalto (str "Haluatko varmasti poistaa liitteen " (:nimi liite) "?")
                    :hyvaksy "Poista"
                    :toiminto-fn (fn []
                                   (poista-fn (:id liite)))}))}
   (ikonit/livicon-trash)])

(defn liitetiedosto
  "Näyttää liitteen pikkukuvan ja nimen.

  Optiot:
  salli-poisto?                      Piirtää roskakorin liitteen nimen viereen
  poista-liite-fn                    Funktio, jota kutsutaan roskakorista"
  ([tiedosto] (liitetiedosto tiedosto {}))
  ([tiedosto {:keys [salli-poisto? poista-liite-fn] :as optiot}]
   (let [nimi (:nimi tiedosto)]
     [:div.liite
      (if (naytettava-liite? tiedosto)
        [:span
         [:img.pikkukuva.klikattava {:src (k/pikkukuva-url (:id tiedosto))
                                     :on-click #(nayta-liite-modalissa tiedosto)}]
         [:span.liite-nimi nimi]
         (when salli-poisto?
           [liitteen-poisto tiedosto poista-liite-fn])]
        [:span
         [:a.liite-linkki {:target "_blank" :href (k/liite-url (:id tiedosto))} nimi]
         (when salli-poisto?
           [liitteen-poisto tiedosto poista-liite-fn])])])))

(defn liitelinkki
  "Näyttää liitteen tekstilinkkinä (teksti voi olla myös ikoni).
   Näytettävät liitteet avataan modaalissa, muutan tarjotaan normaali latauslinkki.

   Optiot:
   nayta-tooltip?                     Näyttää liitteen nimen kun hiirtä pidetään linkin päällä (oletus true)
   rivita?                            Rivittää liitelinkit omille riveille
   salli-poisto?                      Piirtää roskakorin liitteen nimen viereen
   poista-liite-fn        Funktio, jota kutsutaan roskakorista"
  ([liite teksti] (liitelinkki liite teksti {}))
  ([liite teksti {:keys [nayta-tooltip? rivita? salli-poisto? poista-liite-fn] :as optiot}]
   [:span {:style (when rivita? {:display "block"})}
    (if (naytettava-liite? liite)
      [:span
       [:a.klikattava {:title (let [tooltip (:nimi liite)]
                                (if (nil? nayta-tooltip?)
                                  tooltip
                                  (when nayta-tooltip? tooltip)))
                       :on-click #(do
                                    (.stopPropagation %)
                                    (nayta-liite-modalissa liite))}
        teksti]
       (when salli-poisto?
         [liitteen-poisto liite poista-liite-fn])]
      [:span
       [:a.klikattava {:title (:nimi liite)
                       :href (k/liite-url (:id liite))
                       :target "_blank"}
        teksti]
       (when salli-poisto?
         [liitteen-poisto liite poista-liite-fn])])]))

(defn liitteet-numeroina
  "Listaa liitteet numeroina."
  [liitteet]
  [:div.liitteet-numeroina
   (map-indexed
     (fn [index liite]
       ^{:key (:id liite)}
       [:span
        [liitelinkki liite (inc index)]
        [:span " "]])
     liitteet)])

(defn liite-ikonina
  "Näyttää liitteen ikonina."
  ;; PENDING Olisipa kiva jos ikoni heijastelisi tiedoston tyyppiä :-)
  [liite]
  [:span
   [liitelinkki liite (ikonit/file)]
   [:span " "]])

(defn liitteet-ikoneina
  "Listaa liitteet ikoneita."
  [liitteet]
  [:span.liitteet-ikoneina
   (map
     (fn [liite]
       ^{:key (:id liite)}
       [liite-ikonina liite])
     liitteet)])

(defn liitteet-listalla
  "Listaa liitteet leijuvalla listalla."
  [liitteet]
  [:ul.livi-alasvetolista.liitelistaus
   (doall
     (for [liite liitteet]
       ^{:key (hash liite)}
       [:li.harja-alasvetolistaitemi
        [liitelinkki
         liite
         (:nimi liite)
         {:nayta-tooltip? false}]]))])

(defn liitteet-ikonilistana
  "Listaa liitteen ikonina, jota klikkaamalla liitteen voi avata.
   Jos liitteitä on useita, näyttää silti vain yhden ikonin, josta aukeaa lista liitteistä."
  [liitteet]
  (let [lista-auki? (atom false)]
    (komp/luo
      (komp/klikattu-ulkopuolelle #(reset! lista-auki? false))
      (fn [liitteet]
        [:span
         (cond (= (count liitteet) 1)
               [liitelinkki (first liitteet) (ikonit/file)]
               (> (count liitteet) 1)
               [:a.klikattava
                {:on-click (fn []
                             (swap! lista-auki? not))}
                (ikonit/file)])
         (when @lista-auki?
           [liitteet-listalla liitteet])]))))

(defn lisaa-liite
  "Liitetiedosto (file input) komponentti yhden tiedoston lataamiselle.
  Lataa tiedoston serverille ja palauttaa callbackille tiedon onnistuneesta
  tiedoston lataamisesta. Mahdollistaa myös annetun liitteen vaihtamisen.

  HUOM! Oikeustarkistuksen tekeminen on kutsujan vastuulla!

  Optiot voi sisältää:
  grid?                     Jos true, optimoidaan näytettäväksi gridissä.
  nappi-teksti              Teksti, joka napissa näytetään (vakiona 'Lisää liite')
  liite-ladattu             Funktio, jota kutsutaan kun liite on ladattu onnistuneesti.
                            Parametriksi annetaan mäppi, jossa liitteen tiedot:

                            :kuvaus, :fileyard-hash, :urakka, :nimi,
                            :id,:lahde,:tyyppi, :koko 65528

  disabled?                 Nappi disabloitu, true tai false.
  lisaa-usea-liite?         Jos true, komponentilla voi lisätä useita liitteitä.
  nayta-lisatyt-liitteet?   Näyttää juuri lisätyt liitteet, oletus true."
  [urakka-id opts]
  (let [;; Ladatun tiedoston tiedot, kun lataus valmis
        tiedosto (atom nil) ; Jos komponentilla lisätään vain yksi liite
        tiedostot (atom (or (:palautetut-liitteet opts) [])) ; Jos komponentilla lisätään useampi liite
        edistyminen (atom nil) ; Edistyminen, kun lataus on menossa (nil jos ei lataus menossa)
        virheviesti (atom nil)]
    (fn [urakka-id {:keys [liite-ladattu nappi-teksti grid? disabled? lisaa-usea-liite?
                           nayta-lisatyt-liitteet? salli-poistaa-lisatty-liite?
                           poista-lisatty-liite-fn] :as opts}]
      (let [nayta-lisatyt-liitteet? (if (some? nayta-lisatyt-liitteet?) nayta-lisatyt-liitteet? true)
            poista-liite (fn [liite-id]
                           (reset! tiedostot (filter #(not= (:id %) liite-id) @tiedostot))
                           (reset! tiedosto nil)
                           (poista-lisatty-liite-fn liite-id))
            nayta-liite (fn [liite]
                          (if grid?
                            [liitelinkki liite (lyhenna-pitkan-liitteen-nimi (:nimi liite))
                             {:rivita? true
                              :salli-poisto? salli-poistaa-lisatty-liite?
                              :poista-liite-fn poista-liite}]
                            [liitetiedosto liite {:salli-poisto? salli-poistaa-lisatty-liite?
                                                  :poista-liite-fn poista-liite}]))]
        [:span
         ;; Näytä vastikään ladattu liite / liitteet
         (when (and nayta-lisatyt-liitteet? @tiedosto)
           [nayta-liite @tiedosto])
         (when (and nayta-lisatyt-liitteet? lisaa-usea-liite? (not (empty? @tiedostot)))
           (for [liite @tiedostot]
             ^{:key (:id liite)}
             [nayta-liite liite]))

         (if-let [edistyminen @edistyminen]
           ;; Siirto menossa, näytetään progress
           [:progress {:value edistyminen :max 100}]
           ;; Näytetään uuden liitteen lisäyspainike
           [:span.liitekomponentti
            [:div {:class (str "file-upload nappi-toissijainen "
                               (when grid? "nappi-grid ")
                               (when disabled? "disabled "))
                   :on-click #(.stopPropagation %)}
             [ikonit/ikoni-ja-teksti
              (ikonit/livicon-upload)
              (if @tiedosto
                (str "Vaihda liite")
                (or nappi-teksti "Lisää liite"))]
             [:input.upload
              {:type "file"
               :on-change #(let [ch (k/laheta-liite! (.-target %) urakka-id)]
                             (go
                               (loop [ed (<! ch)]
                                 (if (number? ed)
                                   (do (reset! edistyminen ed)
                                       (recur (<! ch)))
                                   (if (and ed (not (k/virhe? ed)))
                                     (do
                                       (reset! edistyminen nil)
                                       (reset! virheviesti nil)
                                       (when liite-ladattu
                                         (if lisaa-usea-liite?
                                           (swap! tiedostot conj ed)
                                           (reset! tiedosto ed))

                                         (liite-ladattu ed)))
                                     (do
                                       (log "Virhe: " (pr-str ed))
                                       (reset! edistyminen nil)
                                       (reset! virheviesti (str "Liitteen lisääminen epäonnistui"
                                                                (if (:viesti ed)
                                                                  (str " (" (:viesti ed) ")"))))))))))}]]
            [:div.liite-virheviesti @virheviesti]])]))))

(defn liitteet-ja-lisays
  "Listaa nykyiset (kantaan tallennetut) liitteet ja näyttää Lisää liite -napin,
   jolla voi lisätä yhden uuden liitteen (optiolla useamman).
   Tekee myös oikeustarkistuksen.

   urakka-id                           Urakan id, johon liite lisätään
   tallennetut-liitteet                Kokoelma liitteitä, jotka on tallennettu kantaan ja jotka on linkitetty
                                       johonkin domainiin liittyvään asiaan

   Optiot voi sisältää:
   uusi-liite-teksti                   Teksti uuden liitteen lisäämisen nappiin
   uusi-liite-atom                     Atomi, johon uuden liitteen tiedot tallennetaan
   grid?                               Jos true, optimoidaan näytettäväksi gridissä
   disabled?                           Disabloidaanko lisäysnappi, true tai false
   lisaa-usea-liite?                   Jos true, mahdollistaa usean liitteen lisäämisen. Oletus false.
   nayta-lisatyt-liitteet?             Listaa juuri lisätyt liitteet (jotka odottavat esim. lomakkeen
                                       tallennuksen yhteydessä tehtävää linkitystä).
                                       Oletus true. Tulisi olla false, mikäli liite-linkitykset tehdään
                                       välittömästi sen jälkeen kun liite on ladattu palvelimelle.
   salli-poistaa-tallennettu-liite?    Jos true, sallii poistaa kantaan jo tallennetun liitteen linkityksen.
   poista-tallennettu-liite-fn         Funktio, jota kutsutaan, kun tallennettu liite vahvistetaan poistettavaksi.
   salli-poistaa-lisatty-liite?        Jos true, sallii poistaa juuri lisätyt liitteet
                                       (jotka odottavat esim. lomakkeen tallennuksen yhteydessä tehtävää linkitystä).
                                       Oletus false.
   poista-lisatty-liite-fn             Funktio, jota kutsutaan, kun juuri lisätty liite vahvistetaan poistettavaksi.
   palautetut-liitteet                 Kokoelma liitteitä, jotka on tallennettu local storageen ja tulisi sen takia
                                       näkyä käyttäjälle."
  [urakka-id tallennetut-liitteet {:keys [uusi-liite-teksti uusi-liite-atom grid? disabled? lisaa-usea-liite?
                                          nayta-lisatyt-liitteet? salli-poistaa-tallennettu-liite?
                                          poista-tallennettu-liite-fn salli-poistaa-lisatty-liite?
                                          poista-lisatty-liite-fn palautetut-liitteet]}]
  [:span
   ;; Näytä olemassaolevat (kantaan tallennetut) liitteet
   (when (oikeudet/voi-lukea? oikeudet/urakat-liitteet urakka-id)
     (for [liite tallennetut-liitteet]
       (if grid?
         ^{:key (:id liite)}
         [liitelinkki liite (lyhenna-pitkan-liitteen-nimi (:nimi liite))
          {:rivita? true
           :salli-poisto? salli-poistaa-tallennettu-liite?
           :poista-liite-fn poista-tallennettu-liite-fn}]
         ^{:key (:id liite)}
         [liitetiedosto liite {:salli-poisto? salli-poistaa-tallennettu-liite?
                               :poista-liite-fn poista-tallennettu-liite-fn}])))

   ;; Uuden liitteen lähetys
   (when (oikeudet/voi-kirjoittaa? oikeudet/urakat-liitteet urakka-id)
     (when uusi-liite-atom
       [lisaa-liite urakka-id {:liite-ladattu #(reset! uusi-liite-atom %)
                               :nappi-teksti uusi-liite-teksti
                               :grid? grid?
                               :lisaa-usea-liite? lisaa-usea-liite?
                               :nayta-lisatyt-liitteet? nayta-lisatyt-liitteet?
                               :salli-poistaa-lisatty-liite? salli-poistaa-lisatty-liite?
                               :poista-lisatty-liite-fn poista-lisatty-liite-fn
                               :disabled? disabled?
                               :palautetut-liitteet palautetut-liitteet}]))])

(defn lataa-tiedosto
  "Ladataan käyttäjän valitsema tiedosto palvelimelle (file input) komponentti yhden tiedoston lataamiselle.
  Lataa tiedoston serverille ja palauttaa callbackille tiedon onnistuneesta
  tiedoston lataamisesta.

  HUOM! Oikeustarkistuksen tekeminen on kutsujan vastuulla!

  Optiot voi sisältää:
  url                       Backend osoite, johon tiedosto lähetetään
  grid?                     Jos true, optimoidaan näytettäväksi gridissä.
  nappi-teksti              Teksti, joka napissa näytetään (vakiona 'Lataa tiedosto')
  lataus-onnistui           Funktio, jota kutsutaan, kun tiedosto on ladattu onnistuneesti.
  lataus-epaonnistui        Funktio, jota kutsutaan, kun tiedoston lataus ei onnistunut.
  disabled?                 Nappi disabloitu, true tai false.
  nappi-luokka              Voidaan tällä hetkellä tehdä napiton-nappi"
  [urakka-id opts]
  (let [;; Ladatun tiedoston tiedot, kun lataus valmis
        ;_ (js/console.log "Debug: renderöidään latausnappi)
        ]
    (fn [urakka-id {:keys [tiedosto-ladattu lataus-epaonnistui nappi-luokka nappi-teksti grid? disabled? url] :as opts}]
      [:span
       [:span.liitekomponentti
        [:div {:class (str "file-upload nappi-toissijainen "
                           (when grid? "nappi-grid ")
                           (when disabled? "disabled ")
                           (when nappi-luokka (str nappi-luokka " ")))
               :on-click #(.stopPropagation %)}
         [ikonit/ikoni-ja-teksti (ikonit/livicon-upload) (or nappi-teksti "Lataa tiedosto")]
         [:input.upload
          {:type "file"
           :on-input #(do
                        (k/laheta-tiedosto! url (.-target %) urakka-id tiedosto-ladattu lataus-epaonnistui)
                        ;; Tyhjennä arvo latauksen jälkeen, jotta samanniminen tiedosto voidaan tarvittaessa lähettää
                        ;; uudestaan.
                        (set! (.-value (.-target %)) nil))}]]]])))
