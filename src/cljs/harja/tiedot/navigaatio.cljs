(ns harja.tiedot.navigaatio
  "Tämä nimiavaruus hallinnoi sovelluksen navigoinnin. Sisältää atomit, joilla eri sivuja ja polkua
  sovelluksessa ohjataan sekä kytkeytyy selaimen osoitepalkin #-polkuun ja historiaan. Tämä nimiavaruus
  ei viittaa itse näkymiin, vaan näkymät voivat hakea täältä tarvitsemansa navigointitiedot."

  (:require
    ;; Reititykset
    [goog.events :as events]
    [goog.Uri :as Uri]
    [goog.history.EventType :as EventType]
    [reagent.core :refer [atom wrap]]
    [cljs.core.async :refer [<! >! chan close!]]

    [harja.loki :refer [log tarkkaile!]]
    [harja.asiakas.tapahtumat :as t]
    [harja.tiedot.urakoitsijat :as urk]
    [harja.tiedot.hallintayksikot :as hy]
    [harja.tiedot.istunto :as istunto]
    [harja.tiedot.urakat :as ur]
    [harja.tiedot.raportit :as raportit]
    [harja.tiedot.navigaatio.reitit :as reitit]
    [harja.tiedot.hallinta.integraatioloki :as integraatioloki]
    [harja.atom :refer-macros [reaction<! reaction-writable]]
    [harja.pvm :as pvm]
    [clojure.string :as str]
    [harja.geo :as geo]
    [harja.domain.oikeudet :as oikeudet]
    [harja.domain.urakka :as urakka-domain]
    [taoensso.timbre :as log])

  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]])

  (:import goog.History))


(def valittu-valilehti reitit/valittu-valilehti)
(def valittu-valilehti-atom reitit/valittu-valilehti-atom)
(def aseta-valittu-valilehti! reitit/aseta-valittu-valilehti!)

(def valittu-sivu (reaction (get @reitit/url-navigaatio :sivu)))

(declare kasittele-url! paivita-url valitse-urakka!)

(defonce murupolku-nakyvissa? (reaction (and (not @raportit/raportit-nakymassa?)
                                             (not= @valittu-sivu :tilannekuva)
                                             (not= @valittu-sivu :tieluvat)
                                             (not= @valittu-sivu :about)
                                             (not= @valittu-sivu :hallinta))))

(defonce kartan-extent (atom nil))

(defonce kartalla-nakyva-alue
         ;; Näkyvä alue reaktoi siihen mihin zoomataan, mutta kun käyttäjä
         ;; muuttaa zoom-tasoa tai raahaa karttaa, se asetetaan näkyvään alueeseen.
         (atom
           (let [[minx miny maxx maxy] @kartan-extent]
             {:xmin minx :ymin miny
              :xmax maxx :ymax maxy})))

(def kartan-nakyvan-alueen-koko
  (reaction
    ((comp geo/extent-hypotenuusa (juxt :xmin :ymin :xmax :ymax))
      @kartalla-nakyva-alue)))

;; Kartan koko voi olla
;; :hidden (ei näy mitään)
;; :S (näkyy Näytä kartta -nappi)
;; :M (matalampi täysleveä)
;; :L (korkeampi täysleveä)
(defonce ^{:doc "Kartan koko"} kartan-kokovalinta (atom :S))

(defn vaihda-kartan-koko! [uusi-koko]
  (let [vanha-koko @kartan-kokovalinta]
    (when uusi-koko
      (reset! kartan-kokovalinta uusi-koko)
      (t/julkaise! {:aihe :kartan-koko-vaihdettu
                    :vanha-koko vanha-koko
                    :uusi-koko uusi-koko}))))

(def valittu-vaylamuoto "Tällä hetkellä valittu väylämuoto" (atom :tie))

(defn vaihda-vaylamuoto! [ut]
  ;; Tämä muutetaan jos tulee Rata
  (reset! valittu-vaylamuoto (if (= :vesivayla (:arvo ut)) :vesi :tie)))

(def +urakkatyypit+
  (filterv
    some?
    [{:nimi "Hoito" :arvo :hoito}
     {:nimi "Tiemerkintä" :arvo :tiemerkinta}
     {:nimi "Päällystys" :arvo :paallystys}
     {:nimi "Paikkaus" :arvo :paikkaus}
     {:nimi "Valaistus" :arvo :valaistus}
     {:nimi "Siltakorjaus" :arvo :siltakorjaus}
     {:nimi "Tekniset laitteet" :arvo :tekniset-laitteet}
     ;; "Vesiväylät" ei ole urakkatyyppi, vaan väylämuoto
     ;; Vesi-väylämuotoon liittyy todellisuudessa monia urakkatyyppejä,
     ;; kuten hoito, ruoppaus, turvalaitteden-korjaus.. kuitenkin toistaiseksi
     ;; näitä kaikkia tyyppejä käsitellään Harjan käyttöliittymässä samalla tavalla.
     ;; Myös kanavien hoito on Vesiväylät-väylämuodon alla. Siksi kanavat-vaihtoehto poistettu.
     (when (istunto/ominaisuus-kaytossa? :vesivayla) {:nimi "Vesiväylät ja kanavat" :arvo :vesivayla})]))

(def +urakkatyypit-ja-kaikki+
  (into [{:nimi "Kaikki" :arvo :kaikki}]
        +urakkatyypit+))

(defn urakkatyyppi-arvolle [tyyppi]
  (when tyyppi
    (let [tyyppi (if (str/starts-with? (name tyyppi) "vesivayla")
                   :vesivayla
                   tyyppi)]
      (first (filter #(= tyyppi (:arvo %))
                     +urakkatyypit+)))))

(defn nayta-urakkatyyppi [tyyppi]
  (when tyyppi
    (let [tyyppi (if (str/starts-with? (name tyyppi) "vesivayla")
                   :vesivayla
                   tyyppi)]
      (:nimi (first
               (filter #(= tyyppi (:arvo %))
                       +urakkatyypit+))))))

(defn urakkatyyppi-urakalle [ur]
  (when ur
    (urakkatyyppi-arvolle (if (urakka-domain/vesivaylaurakka? ur)
                            :vesivayla
                            (:tyyppi ur)))))

(def valittu-urakoitsija "Suodatusta varten valittu urakoitsija
                         tätä valintaa voi käyttää esim. alueurakoitden
                         urakoitsijakohtaiseen suodatukseen" (atom nil)) ;;(= nil kaikki)

;; Hallintayksikön valinta id:llä (URL parametrista)
(defonce valittu-hallintayksikko-id (atom nil))
;; Atomi, joka sisältää valitun hallintayksikön
(defonce valittu-hallintayksikko
         (reaction (let [id @valittu-hallintayksikko-id
                         yksikot @hy/vaylamuodon-hallintayksikot]
                     (when (and id yksikot)
                       (some #(and (= id (:id %)) %) yksikot)))))

;; Jos urakka valitaan id:n perusteella (url parametrilla), asetetaan se tänne
(defonce valittu-urakka-id (atom nil))

;; Atomi, joka sisältää valitun hallintayksikön urakat
(defonce hallintayksikon-urakkalista
         (reaction<! [yks @valittu-hallintayksikko]
                     (when yks
                       (ur/hae-hallintayksikon-urakat yks))))

;; Atomi, joka sisältää valitun urakan (tai nil)
;; Älä resetoi tätä suoraan, vaan urakan vaihtuessa resetoi valittu-urakka-id
(defonce valittu-urakka
         (reaction
           (let [id @valittu-urakka-id
                 urakat @hallintayksikon-urakkalista]
             (when (and id urakat)
               (some #(when (= id (:id %)) %) urakat)))))


;; Käyttäjän asettama urakkatyyppi. Todellinen UI:lla näkyvästi valittu urakkatyyppi
;; ei kuitenkaan ole tämä, vaan alla oleva reaction (lue sitä, älä tätä)
;; Älä myöskään aseta suoraan, käytä vaihda-urakkatyyppi!
(defonce ^{:private true} valittu-urakkatyyppi (atom nil))

;; Tällä hetkellä valittu väylämuodosta riippuvainen urakkatyyppi
;; Jos käyttäjällä urakkarooleja, valitaan urakoista yleisin urakkatyyppi
(defonce urakkatyyppi
         (reaction<! [kayttajan-oletus-tyyppi (:urakkatyyppi @istunto/kayttaja)

                      ;; Jos urakka on valittuna, asetetaan tyypiksi sen tyyppi
                      urakan-urakkatyyppi (urakkatyyppi-urakalle @valittu-urakka)
                      ;; Jos urakkatyyppi valitaan murupolusta, asetetaan se tyypiksi
                      valittu-urakkatyyppi @valittu-urakkatyyppi
                      ;; Lopuksi tarkastetaan, onko käyttäjällä oletustyyppiä
                      oletus-urakkatyyppi (urakkatyyppi-arvolle (:urakkatyyppi @istunto/kayttaja))
                      valittu-hy-id @valittu-hallintayksikko-id]

                     (go
                       (or urakan-urakkatyyppi
                           valittu-urakkatyyppi
                           ;; Jos hallintayksikkö on valittuna, asetetaan urakkatyypiksi hallintayksikölle
                           ;; sopiva urakkatyyppi. Jos hallintayksikön väylämuoto on :tie, tarkastetaan,
                           ;; onko käyttäjällä oletustyyppiä. Jos ei ole, palautetaan oletuksena :hoito
                           ;; Koska hallintayksiköistä ei ole välttämättä vielä haettu, täytyy tässä
                           ;; ottaa huomioon asynkronisuus.
                           (when valittu-hy-id
                             (urakkatyyppi-arvolle
                               (case (<! (hy/hallintayksikon-vaylamuoto valittu-hy-id))
                                 :tie
                                 (if-not (= :vesivayla kayttajan-oletus-tyyppi)
                                   kayttajan-oletus-tyyppi
                                   :hoito)

                                 :vesi
                                 :vesivayla
                                 nil)))
                           oletus-urakkatyyppi))))

(defn vaihda-urakkatyyppi!
  "Vaihtaa urakkatyypin ja resetoi valitun urakoitsijan, jos kyseinen urakoitsija ei
   löydy valitun tyyppisten urakoitsijain listasta."
  [ut]
  (go
    (when (not= @valittu-urakkatyyppi ut)
      (valitse-urakka! nil))
    (reset! valittu-urakkatyyppi ut)
    (vaihda-vaylamuoto! ut)
    (<! (hy/aseta-hallintayksikot-vaylamuodolle! @valittu-vaylamuoto))
    (swap! valittu-urakoitsija
           #(let [nykyisen-urakkatyypin-urakoitsijat
                  (case (:arvo ut)
                    :kaikki @urk/urakoitsijat-kaikki
                    :hoito @urk/urakoitsijat-hoito
                    :paallystys @urk/urakoitsijat-paallystys
                    :paikkaus @urk/urakoitsijat-paikkaus
                    :tiemerkinta @urk/urakoitsijat-tiemerkinta
                    :valaistus @urk/urakoitsijat-valaistus
                    :siltakorjaus @urk/urakoitsijat-siltakorjaus
                    :tekniset-laitteet @urk/urakoitsijat-tekniset-laitteet
                    :vesivayla @urk/urakoitsijat-vesivaylat)]
              (if (nykyisen-urakkatyypin-urakoitsijat (:id %))
                %
                nil)))))

(def tarvitsen-isoa-karttaa "Set käyttöliittymänäkymiä (keyword), jotka haluavat pakottaa kartan näkyviin.
  Jos tässä setissä on itemeitä, tulisi kartta pakottaa näkyviin :L kokoisena vaikka se ei olisikaan muuten näkyvissä."
  (atom #{}))

;; jos haluat palauttaa kartan edelliseen kokoon, säilö edellinen koko tähän (esim. Valitse kartalta -toiminto)
(def kartan-edellinen-koko (atom nil))

(def kartan-koko
  "Kartan laskettu koko riippuu kartan kokovalinnasta sekä kartan pakotteista."
  (reaction (let [valittu-koko @kartan-kokovalinta
                  sivu (valittu-valilehti :sivu)
                  v-ur @valittu-urakka
                  tarvitsen-isoa-karttaa @tarvitsen-isoa-karttaa]
              (if-not (empty? tarvitsen-isoa-karttaa)
                :L
                ;; Ei kartan pakotteita, tehdään sivukohtaisia special caseja
                ;; tai palautetaan käyttäjän valitsema koko
                (cond (and
                        (= sivu :hallinta)
                        ;; Hallintavälilehdellä ei näytetä karttaa, paitsi jos ollaan kanavaurakoiden kohteiden luonnissa
                        ;; Tähän tarvitaan molemmat tarkastukset, koska vesiväylä-hallinan valituksi välilehdeksi
                        ;; voi jäädä kohteiden luonti, vaikka siirryttäisikiin esim integraatiolokiin
                        (not= (valittu-valilehti :hallinta) :vesivayla-hallinta)
                        (not= (valittu-valilehti :vesivayla-hallinta) :kanavaurakoiden-kohteiden-luonti))
                      :hidden

                      (= sivu :about) :hidden

                      (= sivu :tilannekuva) :XL

                      (and (= sivu :urakat)
                           (not v-ur)) :XL
                      :default valittu-koko)))))

(def kartta-nakyvissa?
  "Kartta ei piilotettu"
  (reaction (let [koko @kartan-koko]
              (and (not= :S koko)
                   (not= :hidden koko)))))


(def kartan-kontrollit-nakyvissa?
  (reaction
    (let [sivu (valittu-valilehti :sivu)]
      ;; Näytetään kartta jos karttaa ei ole pakotettu näkyviin,
      ;; JA ei olla tilannekuvassa, JA joko ei olla urakoissa TAI urakkaa ei ole valittu.
      (and
        (empty? @tarvitsen-isoa-karttaa)
        (not= sivu :tilannekuva)
        (or
          (not= sivu :urakat)
          (some? @valittu-urakka))))))

(defn aseta-hallintayksikko-ja-urakka [hy-id ur]
  (reset! valittu-hallintayksikko-id hy-id)
  ;; go block sen takia, että vaihda-urakkatyyppi! kerkeää suorittaa
  ;; hy/aseta-hallintayksikot-vaylamuodolle! funktion ennen, kuin valitse-urakka!
  ;; funktiota kutsutaan, sillä valitse-urakka! triggeröi kasittele-url! funktion
  ;; joka resetoisi valittu-hallintayksikko-id:n nilliksi.
  (go (<! (vaihda-urakkatyyppi! (urakkatyyppi-urakalle ur)))
      (valitse-urakka! ur)))

(defn aseta-hallintayksikko-ja-urakka-id! [hy-id ur-id]
  (log/info "ASETA HY: " hy-id ", UR: " ur-id)
  (reset! valittu-hallintayksikko-id hy-id)
  (reset! valittu-urakka-id ur-id))

(defn valitse-urakoitsija! [u]
  (reset! valittu-urakoitsija u))

;; Rajapinta hallintayksikön valitsemiseen, jota viewit voivat kutsua
(defn valitse-hallintayksikko-id! [id]
  (reset! valittu-hallintayksikko-id id)
  (reset! valittu-urakka-id nil)
  (paivita-url))

(defn valitse-hallintayksikko! [yks]
  (valitse-hallintayksikko-id! (:id yks)))

(defonce ilmoita-hallintayksikkovalinnasta
         (run! (let [yks @valittu-hallintayksikko]
                 (if yks
                   (t/julkaise! (assoc yks :aihe :hallintayksikko-valittu))
                   (t/julkaise! {:aihe :hallintayksikkovalinta-poistettu})))))

(defn valitse-urakka-id! [id]
  (reset! valittu-urakka-id id)
  (paivita-url))

(defn valitse-urakka! [ur]
  (valitse-urakka-id! (:id ur))
  (log "VALITTIIN URAKKA: " (pr-str (dissoc ur :alue))))

(defonce ilmoita-urakkavalinnasta
         (run! (let [ur @valittu-urakka]
                 (if ur
                   (t/julkaise! (assoc ur :aihe :urakka-valittu))
                   (t/julkaise! {:aihe :urakkavalinta-poistettu})))))

(defonce urakka-klikkaus-kuuntelija
         (t/kuuntele! :urakka-klikattu
                      ;; FIXME: tämä pitäisi faktoroida elegantimmaksi
                      ;; joku tapa pitää olla sanoa, mitä halutaan tapahtuvan kun urakkaa
                      ;; klikataan
                      ;;
                      ;; Ehkä joku pino kartan valintatapahtumien kuuntelijoita, jonne voi lisätä
                      ;; itsensä ja ne ajettaisiin uusin ensin. Jos palauttaa true, ei ajeta muita.
                      ;; Silloin komponentti voisi ylikirjoittaa valintatapahtumien käsittelyn.

                      (fn [urakka]
                        ;;(log "KLIKATTU URAKKAA: " (:nimi urakka))
                        (valitse-urakka! urakka))))

;; Quick and dirty history configuration.
(defonce historia (let [h (History. false)]
                    (events/listen h EventType/NAVIGATE
                                   #(kasittele-url! (.-token %)))
                    h))

(defn nykyinen-url []
  (str (reitit/muodosta-polku @reitit/url-navigaatio)
       "?"
       (when-let [hy @valittu-hallintayksikko-id] (str "&hy=" hy))
       (when-let [u @valittu-urakka-id] (str "&u=" u))))

(defonce ^{:doc "Tämä lippu voi estää URL tokenin päivittämisen, käytetään siirtymissä, joissa
 halutaan tehdä useita muutoksia ilman että välissä pävitetään URLia keskeneräisenä."}
         esta-url-paivitys? (cljs.core/atom false))


;; asettaa oikean sisällön urliin ohjelman tilan perusteella
(defn paivita-url []
  (when-not @esta-url-paivitys?
    (let [url (nykyinen-url)]
      (when (not= url (.-token historia))
        (log "URL != token :: " url " != " (.getToken historia))
        (.setToken historia url)))))

(defn esta-url-paivitys!
  "Estä URL päivitykset kunnes salli-url-paivitys! kutsutaan."
  []
  (reset! esta-url-paivitys? true))

(defn salli-url-paivitys!
  "Salli URL päivitys ja tee päivitys nyt"
  []
  (reset! esta-url-paivitys? false)
  (paivita-url))

(defn vaihda-sivu!
  "Vaihda nykyinen sivu haluttuun."
  [uusi-sivu]
  (when-not (= (valittu-valilehti :sivu) uusi-sivu)
    (reitit/aseta-valittu-valilehti! :sivu uusi-sivu)))

(def suodatettu-urakkalista "Urakat suodatettuna urakkatyypin ja urakoitsijan mukaan."
  (reaction
    (let [v-ur-tyyppi (:arvo @urakkatyyppi)
          v-urk @valittu-urakoitsija
          urakkalista @hallintayksikon-urakkalista
          kayttajan-urakat (set (map key (:urakkaroolit @istunto/kayttaja)))]
      (into []
            (comp (filter #(or (= :kaikki v-ur-tyyppi)
                               (= v-ur-tyyppi (:tyyppi %))
                               (and (= v-ur-tyyppi :vesivayla)
                                    (urakka-domain/vesivaylaurakka? %))))
                  (filter #(or
                             (kayttajan-urakat (:id %))
                             (or (nil? v-urk) (= (:id v-urk) (:id (:urakoitsija %))))))
                  (filter #(oikeudet/voi-lukea? oikeudet/urakat (:id %) @istunto/kayttaja)))
            urakkalista))))

(def urakat-kartalla "Sisältää suodatetuista urakoista aktiiviset"
  (reaction (into []
                  (filter #(pvm/ennen? (pvm/nyt) (:loppupvm %)))
                  @suodatettu-urakkalista)))


(def render-lupa-hy? (reaction
                       (some? @hy/vaylamuodon-hallintayksikot)))

(def render-lupa-u? (reaction
                      (or (nil? @valittu-urakka-id)         ;; urakkaa ei annettu urlissa, ei estetä latausta
                          (nil? @valittu-hallintayksikko)   ;; hy:tä ei saatu asetettua -> ei estetä latausta
                          (some? @hallintayksikon-urakkalista))))

(def render-lupa-url-kasitelty? (atom false))

;; sulava ensi-render: evätään render-lupa? ennen kuin konteksti on valmiina
(def render-lupa? (reaction
                    (and @render-lupa-hy? @render-lupa-u?
                         @render-lupa-url-kasitelty?)))

(defonce urlia-kasitellaan? (atom false))

(defn kasittele-url!
  "Käsittelee urlin (route) muutokset."
  [url]
  (reset! urlia-kasitellaan? true)
  (go
    (let [uri (Uri/parse url)
          polku (.getPath uri)
          parametrit (.getQueryData uri)]
      (reset! valittu-hallintayksikko-id (some-> parametrit (.get "hy") js/parseInt))
      (reset! valittu-urakka-id (some-> parametrit (.get "u") js/parseInt))
      ;; Kun ollaan aloitusikkunassa, katsotaan mikä on käyttäjän perusurakkatyyppi, jotta voidaan näyttää oikean
      ;; väylämuodon hallintayksiköt
      (when (= polku "urakat/yleiset")
        (let [vesivaylaurakka? (urakka-domain/vesivaylaurakkatyyppi? (:urakkatyyppi @istunto/kayttaja))
              arvo (if vesivaylaurakka?
                     {:arvo :vesivayla}
                     {:arvo :hoito})]
          (vaihda-vaylamuoto! arvo)))
      (when @valittu-hallintayksikko-id
        (reset! valittu-vaylamuoto (<! (hy/hallintayksikon-vaylamuoto @valittu-hallintayksikko-id))))

      (<! (hy/aseta-hallintayksikot-vaylamuodolle! @valittu-vaylamuoto))
      (swap! reitit/url-navigaatio
             reitit/tulkitse-polku polku)
      ;; Käsitellään linkit yksittäisiin integraatiolokin viesteihin
      (when (and (= polku "hallinta/integraatioloki")
                 (.get parametrit "valittu-jarjestelma")
                 (.get parametrit "valittu-integraatio")
                 (.get parametrit "tapahtuma-id")
                 (.get parametrit "alkanut"))
        (let [jarjestelmat (<! (integraatioloki/hae-jarjestelmien-integraatiot))
              jarjestelma (.get parametrit "valittu-jarjestelma")
              alkanut-pvm (pvm/iso-8601->pvm (.get parametrit "alkanut"))]
          (reset! integraatioloki/valittu-jarjestelma (some #(when (= jarjestelma (:jarjestelma %))
                                                               %)
                                                            jarjestelmat))
          (reset! integraatioloki/valittu-integraatio (.get parametrit "valittu-integraatio"))
          (reset! integraatioloki/tapahtuma-id #{(try (js/parseInt (.get parametrit "tapahtuma-id"))
                                                      (catch :default e
                                                        nil))})
          (reset! integraatioloki/nayta-uusimmat-tilassa? false)
          (reset! integraatioloki/valittu-aikavali [alkanut-pvm alkanut-pvm])
          (reset! integraatioloki/tultiin-urlin-kautta true)
          ;; Paivitetään url, jotta parametrit eivät enään näy urlissa
          (paivita-url))))
    (reset! render-lupa-url-kasitelty? true)
    (log "Render-lupa annettu!")
    (t/julkaise! {:aihe :url-muuttui :url url})
    (reset! urlia-kasitellaan? false)))

(.setEnabled historia true)

(defonce paivita-url-navigaatiotilan-muuttuessa
         (add-watch reitit/url-navigaatio
                    ::url-muutos
                    (fn [_ _ vanha uusi]
                      (when (and (not @urlia-kasitellaan?) (not= vanha uusi))
                        (paivita-url)))))

(defn paivita-urakan-tiedot! [urakka-id funktio & args]
  (swap! hallintayksikon-urakkalista
         (fn [urakat]
           (mapv (fn [urakka]
                   (if (= (:id urakka) urakka-id)
                     (apply funktio urakka args)
                     urakka))
                 urakat))))

;; HAR-5517 takia. Tilannekuvassa halutaan näyttää selite urakkarajoille, jos alueita on valittu. Syklisen riippuvuuden takia
;; piti laittaa tänne.
(def tilannekuvassa-alueita-valittu? (atom false))


(defn yllapitourakka-valittu? []
  (let [urakkatyyppi (:arvo @urakkatyyppi)]
    (or (= urakkatyyppi :paallystys)
        (= urakkatyyppi :paikkaus)
        (= urakkatyyppi :tiemerkinta))))
