(ns harja.views.haku
  "Harjan haku"
  (:require [reagent.core :refer [atom] :as r]
            [clojure.string :as str]

            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.listings :refer [suodatettu-lista]]
            [harja.ui.modal :as modal]
            [harja.ui.yleiset :refer [tietoja kaksi-palstaa-otsikkoja-ja-arvoja]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakat :as urakat]
            [harja.atom :refer-macros [reaction<!]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.istunto :as istunto])

  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]))

(def hakutermi (atom ""))

(def hakutulokset
  (reaction<! [termi @hakutermi]
              {:odota 500
               :nil-kun-haku-kaynnissa? true}
              (when (> (count termi) 1)
                (k/post! :hae termi))))

(defn nayta-organisaation-yhteystiedot
  [o]
  (modal/nayta! {:otsikko (:nimi o)
                 :luokka "yhteystieto"
                 :footer [:span
                          [:button.nappi-toissijainen {:type "button"
                                                       :on-click #(do (.preventDefault %)
                                                                      (modal/piilota!))}
                           "Sulje"]]}
                [:div.kayttajan-tiedot
                 [tietoja {}
                  "Org. tyyppi:" (name (:tyyppi o))
                  "Y-tunnus:" (:ytunnus o)
                  "Osoite" (:katuosoite o)
                  "Postinumero" (:postinumero o)
                  "Sampoid:" (or (:sampoid o) "Ei annettu")
                  (if (= (:tyyppi o) :hallintayksikko)
                    "Liikennemuoto:" (case (:liikennemuoto o)
                                       "T" "Tie"
                                       "V" "Vesi"
                                       "R" "Rata"
                                       "Ei annettu"))]
                 (when-let [urakat (:urakat o)]
                   [:span
                    [:span.tietokentta (if (= :urakoitsija (:tyyppi o))
                                         "Urakoitsijana urakoissa:"
                                         "Tilaajana urakoissa:")]
                    [:div.mukana-urakoissa
                     (if (empty? urakat)
                       "Ei urakoita"
                       (for [u urakat]
                         ^{:key (:nimi u)}
                         [:li.tietoarvo (:nimi u)]))]])]))

(defn valitse-organisaatio
  [o]
  (if (= :urakoitsija (:tyyppi o))
    (nayta-organisaation-yhteystiedot o)
    (nav/valitse-hallintayksikko! o)))

(defn- kayttajan-tiedot [{org :organisaatio
                          email :sahkoposti
                          roolit :roolit
                          urakkaroolit :urakkaroolit
                          organisaatioroolit :organisaatioroolit
                          :as k}]
  (let [org-nimi (:nimi org)
        org-tyyppi (:tyyppi org)
        uniikit-roolit #(when-not (empty? %)
                         (str/join ", "
                                   (into #{}
                                         (mapcat (partial keep oikeudet/roolin-kuvaus)
                                                 (vals %)))))]
    [:div.kayttajan-tiedot
     [kaksi-palstaa-otsikkoja-ja-arvoja {}
      "Organisaatio:" [:a.klikattava {:on-click #(do (.preventDefault %)
                                                     (modal/piilota!)
                                                     (valitse-organisaatio org))}
                       (when org-nimi org-nimi)]
      "Org. tyyppi:" (when org-tyyppi (name org-tyyppi))
      "Käyttäjänimi:" (get k :kayttajanimi)
      "Puhelin:" (get k :puhelin)
      "Sähköposti:" (when email
                      [:a {:href (str "mailto:" email)}
                       email])
      "Roolit" (when (not (empty? roolit))
                 (str/join ", " (keep oikeudet/roolin-kuvaus roolit)))
      "Urakkaroolit" (uniikit-roolit urakkaroolit)
      "Organisaatioroolit" (uniikit-roolit organisaatioroolit)]]))

(defn nayta-kayttaja
  [k]
  (modal/nayta! {:otsikko (str (:etunimi k) " " (:sukunimi k))
                 :luokka "yhteystieto"
                 :footer [:span
                          [:button.nappi-toissijainen {:type "button"
                                                       :on-click #(do (.preventDefault %)
                                                                      (modal/piilota!))}
                           "Sulje"]]}

                (kayttajan-tiedot k)))

(defn valitse-hakutulos
  [tulos]
  (reset! hakutermi "")
  (go (when-let [valitun-tyyppi (:tyyppi tulos)]
        (case valitun-tyyppi
          :urakka
          (let [haettu-urakka (<! (k/post! :hae-urakka (:id tulos)))
                ;; hae hallintayksikön urakat jo tässä, jottei urakkaa aseteta ennen kuin
                ;; urakkalista on päivittynyt. Muuten voi tulla ajoitusongelma toisinaan HAR-2044
                hallintayksikon-urakkalista (<! (urakat/hae-hallintayksikon-urakat
                                                  {:id (get-in haettu-urakka [:hallintayksikko :id])}))]
            (reset! nav/hallintayksikon-urakkalista hallintayksikon-urakkalista)
            (nav/aseta-hallintayksikko-ja-urakka
              (get-in haettu-urakka [:hallintayksikko :id])
              haettu-urakka))
          :kayttaja (let [haettu-kayttaja (<! (k/post! :hae-kayttajan-tiedot (:id tulos)))]
                      (nayta-kayttaja haettu-kayttaja))
          :organisaatio
          (let [haettu-organisaatio (<! (k/post! :hae-organisaatio (:id tulos)))]
            (valitse-organisaatio haettu-organisaatio))))))

(defn liikaa-osumia?
  [tulokset]
  (when-let [ryhmitellyt (vals (group-by :tyyppi tulokset))]
    (some #(> (count %) 10) ryhmitellyt)))

(defn haku []
  (komp/luo
    (komp/klikattu-ulkopuolelle #(do (reset! hakutulokset nil)
                                     (reset! hakutermi nil)))
    (fn []
      [:form.navbar-form.navbar-left {:role "search"}
       [:div.form-group.haku
        [suodatettu-lista {:format :hakusanat
                           :haku :hakusanat
                           :term (r/wrap @hakutermi
                                         (fn [uusi-termi]
                                           (reset! hakutermi
                                                   (str/triml (str/replace uusi-termi #"\s{2,}" " ")))))
                           :ryhmittely :tyyppi
                           :ryhman-otsikko #(case %
                                              :urakka "Urakat"
                                              :kayttaja "Käyttäjät"
                                              :organisaatio "Organisaatiot"
                                              "Muut")
                           :on-select #(valitse-hakutulos %)
                           :aputeksti "Hae Harjasta"
                           :tunniste #((juxt :tyyppi :id) %)
                           :vinkki #(when-not (empty? @hakutermi)
                                      (if (liikaa-osumia? @hakutulokset)
                                        "Paljon osumia, tarkenna hakua..."
                                        (if (nil? (:organisaatio @istunto/kayttaja))
                                          "Käyttäjän organisaatiota ei tunnistettu, hakutoiminto ei käytössä. Ota yhteys pääkäyttäjään."
                                          (when (= [] @hakutulokset)
                                           (str "Ei tuloksia haulla " @hakutermi)))))}
         @hakutulokset]]])))
