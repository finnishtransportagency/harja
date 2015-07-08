(ns harja.views.haku
  "Harjan haku"
  (:require [reagent.core :refer [atom] :as reagent]
            [cljs-time.coerce :as tc]
            [clojure.string :as str]

            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.listings :refer [suodatettu-lista]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.tiedot.navigaatio :as nav]
            [harja.atom :refer-macros [reaction<!]])

  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]))


(def hakutermi (atom ""))

(def hakutulokset
  (reaction<! [termi @hakutermi]
              {:odota 500}
              (when (> (count termi) 1)
                (k/post! :hae termi))))

(defn valitse-hakutulos
  [tulos]
  (reset! hakutermi "")
  (go (when-let [valitun-tyyppi (:tyyppi tulos)]
        (case valitun-tyyppi
          :urakka
          (let [haettu-urakka (<! (k/post! :hae-urakka (:id tulos)))]
            (nav/aseta-hallintayksikko-ja-urakka
              (get-in haettu-urakka [:hallintayksikko :id])
              (:id haettu-urakka)))
          ;:kayttaja (reset! nav/valittu-kayttaja (:id tulos))
          ;:organisaatio (reset! nav/valittu-organisaatio (:id tulos))
          ))))

(defn liikaa-osumia?
  [tulokset]
  (when-let [ryhmitellyt (vals (group-by :tyyppi tulokset))]
    (some #(> (count %) 10) ryhmitellyt)))

(defn haku
  []
  [:form.navbar-form.navbar-left {:role "search"}
   [:div.form-group.haku
    [suodatettu-lista {:format         :hakusanat
                       :haku           :hakusanat
                       :term           hakutermi
                       :ryhmittely     :tyyppi
                       :ryhman-otsikko #(case %
                                         :urakka "Urakat"
                                         :kayttaja "Käyttäjät"
                                         :organisaatio "Organisaatiot"
                                         "Muut")
                       :on-select      #(valitse-hakutulos %)
                       :aputeksti      "Hae Harjasta"
                       :tunniste       #((juxt :tyyppi :id) %)
                       :vinkki         #(when (liikaa-osumia? @hakutulokset)
                                         "Liikaa osumia, tarkenna hakua...")}
     @hakutulokset]]])