(ns harja.ui.lomake
  "Lomakeapureita"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.validointi :as validointi]
            [harja.ui.yleiset :refer [virheen-ohje]]
            [harja.ui.kentat :refer [tee-kentta nayta-arvo atomina]]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.ui.komponentti :as komp]
            [taoensso.truss :as truss :refer-macros [have have! have?]]))

(defrecord Ryhma [otsikko optiot skeemat])

(defn ryhma [otsikko-tai-optiot & skeemat]
  (if-let [optiot (and (map? otsikko-tai-optiot) otsikko-tai-optiot)]
    (->Ryhma (:otsikko optiot)
             (merge {:ulkoasu :oletus}
                    optiot)
             skeemat)
    (->Ryhma otsikko-tai-optiot
             {:ulkoasu :oletus} skeemat)))

(defn ryhma? [x]
  (instance? Ryhma x))

(defrecord ^:private Otsikko [otsikko])
(defn- otsikko? [x]
  (instance? Otsikko x))

(defn- pura-ryhmat
  "Purkaa skeemat ryhmistä yhdeksi flat listaksi, jossa ei ole nil arvoja.
Ryhmien otsikot lisätään väliin Otsikko record tyyppinä."
  [skeemat]
  (mapcat (fn [s]
            (if (ryhma? s)
              ;; Lisää ryhmän otsikko ja sen skeemat, jos ryhmä ei ole tyhjä
              (let [skeemat (remove nil? (:skeemat s))]
                (when-not (empty? skeemat)
                  (concat [(->Otsikko (:otsikko s))]
                          skeemat)))
              ;; Muuten lisätään itse skeema, jos se ei ole nil
              (when s
                [s])))
          skeemat))

(defn rivita
  "Rivittää kentät siten, että kaikki palstat tulee täyteen. 
  Uusi rivi alkaa kun palstat ovat täynnä, :uusi-rivi? true on annettu tai tulee uusi ryhmän otsikko."
  [skeemat]
  (loop [rivit []
         rivi []
         palstoja 0
         [s & skeemat] (remove nil? skeemat)               ;(pura-ryhmat  skeemat)
         ]
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
          (do (log "SKEEMA " (pr-str (select-keys s [:nimi :tyyppi :palstoja :otsikko])) ", palstoja: " palstoja)
              (if (or (otsikko? s)
                      (:uusi-rivi? s)
                      (> (+ palstoja kentan-palstat) 2))
                (do (log "SKEEMA UUSI RIVI ALKAA! ")
                    ;; Kenttä on uusi otsikko tai rivi menisi yli 2 palstan => aloitetaan uusi rivi tälle
                    (recur (if (empty? rivi)
                             rivit
                             (conj rivit rivi))
                           [s]
                           (if (otsikko? s) 0 kentan-palstat)
                           skeemat))
                ;; Mahtuu tälle riville, lisätään nykyiseen riviin
                (recur rivit
                       (conj rivi s)
                       (+ palstoja kentan-palstat)
                       skeemat))))))))




(defn kentan-vihje [_ {vihje :vihje}]
  (when vihje
    ;; FIXME: uuteen palstatyyliin
    [:div.row
     [:div.col-sm-2]
     [:div {:class
            (str "inline-block lomake-vihje col-sm-10")}
      [:div.vihjeen-sisalto
       (harja.ui.ikonit/info-sign)
       (str " " vihje)]]]))




(def +ei-otsikkoa+ #{:boolean})

(defn yleinen-huomautus
  "Yleinen huomautus, joka voidaan näyttää esim. lomakkeen tallennuksen yhteydessä"
  [teksti]
  [:div.lomake-yleinen-huomautus (harja.ui.ikonit/info-sign) (str " " teksti)])

(defn lomake-lukittu-huomautus
  ;; FIXME: siirrä jonnekin muualle, ei liity lomakkeeseen mitenkään
  [nykyinen-lukko]
  [:div.lomake-lukittu-huomautus (harja.ui.ikonit/info-sign) (str " Lomakkeen muokkaaminen on estetty, sillä toinen käyttäjä"
   (when (and (:etunimi nykyinen-lukko)
              (:sukunimi nykyinen-lukko))
     (str " (" (:etunimi nykyinen-lukko) " " (:sukunimi nykyinen-lukko) ")"))
    " muokkaa parhaillaan lomaketta. Yritä hetken kuluttua uudelleen.")])

(def +piilota-label+ #{:boolean :tierekisteriosoite})

(defn kentta
  "UI yhdelle kentälle, renderöi otsikon ja "
  [{:keys [palstoja nimi otsikko tyyppi hae fmt rivi? yksikko pakollinen?] :as s} data atom-fn muokattava?]
  (let [arvo (atom-fn s)]
    [:div.form-group {:class (str (if rivi?
                                    "col-xs-3 col-sm-2"
                                    (case (or palstoja 1)
                                      1 "col-sm-6 col-md-5 col-lg-4"
                                      2 "col-sm-12 col-md-10 col-lg-8"))
                                  (when pakollinen?
                                    " required"))}
     (when-not (+piilota-label+ tyyppi)
       [:label.control-label {:for nimi}
        [:span
         [:span.kentan-label otsikko]
         (when yksikko [:span.kentan-yksikko yksikko])]])
     (if (= tyyppi :komponentti)
       [:div.komponentti (:komponentti s)]
       (if muokattava?
         (do (log "TEE-KENTTA: " (pr-str (select-keys s [:nimi :tyyppi])))
             (have #(contains? % :tyyppi) s)
             [tee-kentta (assoc s :lomake? true) arvo])
         [:div.form-control-static
          (if fmt
            (fmt ((or hae #(get % nimi)) data))
            (nayta-arvo kentta arvo))]))]))

(defn rivi
  "UI yhdelle riville"
  [skeemat data atom-fn voi-muokata? nykyinen-fokus aseta-fokus!]
  (let [rivi? (-> skeemat meta :rivi?)]
    [:div.row.lomakerivi
     (doall
      (for [{:keys [nimi muokattava?] :as s} skeemat
            :let [muokattava? (and voi-muokata?
                                   (or (nil? muokattava?)
                                       (muokattava? data)))]]
        ^{:key nimi}
        [kentta (assoc s
                       :rivi? rivi?) data atom-fn muokattava?]))]))

;; FIXME: ulkoinen validointitila on huono idea, pistetään data mäppiin vain nekin

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
                  funktiolle annetaan virheet ja varoitukset parametrina 

  :virheet        atomi, joka sisältää mäpin kentän nimestä virheisiin, jos tätä ei
                  anneta, lomake luo sisäisesti uuden atomin.
  :varoitukset    atomi, joka sisältää mäpin kentän nimestä varoituksiin, jos tätä ei
                  anneta, lomake luo sisäisesti uuden atomin.
  :voi-muokata?   voiko lomaketta muokata, oletuksena true
  "

  [{:keys [otsikko muokkaa! luokka footer footer-fn virheet varoitukset voi-muokata?] :as opts} skeema
   {muokatut ::muokatut
    fokus ::fokus
    :as  data}]
  (let [voi-muokata? (if (some? voi-muokata?)
                       voi-muokata?
                       true)
        luokka (or luokka :horizontal)
        kaikki-skeemat (filter (comp not otsikko?) (pura-ryhmat skeema))
        kaikki-virheet (validointi/validoi-rivi nil data kaikki-skeemat :validoi)
        kaikki-varoitukset (validointi/validoi-rivi nil data kaikki-skeemat :varoita)
        ]
    [:form.lomake
     (when otsikko
       [:h3.lomake-otsikko otsikko])
     (map-indexed (fn [i skeemat]
                    (log "SKEEMA RIVI " (count skeemat) " itemiä, " (pr-str (map :nimi skeemat)))
                    (let [otsikko (when (otsikko? (first skeemat))
                                    (first skeemat))
                          skeemat (if otsikko
                                    (rest skeemat)
                                    skeemat)
                          rivi-ui [rivi skeemat
                                   data
                                   (fn [{nimi :nimi :as s}]
                                     (atomina s data
                                              (fn [uudet-tiedot]
                                                (log "UUDET-TIEDOT: " (pr-str uudet-tiedot))
                                                ;; FIXME: tässä mukaan validointivirheet
                                                (muokkaa! (assoc uudet-tiedot
                                                                 ::muokatut (conj (or muokatut #{})
                                                                                  nimi))))))
                                   voi-muokata?
                                   fokus
                                   #(swap! data assoc ::fokus %)]]
                      (if otsikko
                        ^{:key (:otsikko otsikko)}
                        [:span
                         [:h3.lomake-ryhman-otsikko (:otsikko otsikko)]
                         rivi-ui]
                        ^{:key i}
                        rivi-ui)))
                  (rivita skeema))
     
     (when-let [footer (if footer-fn
                         (footer-fn kaikki-virheet kaikki-varoitukset)
                         footer)]
       [:div.lomake-footer.row
        [:div.col-md-12 footer]])]))
