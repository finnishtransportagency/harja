(ns harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.rekisterointi
  "Komponentin cljs-renderöijät rekisteröidään yhdellä kutsulla `:id`:n alle.
  Rekisteröinnin muoto ja yksikäsitteisyys tarkistetaan JVM-testissä.")

(defonce ^:private renderoijat (atom {}))

(defn register!
  "Rekisteröi komponentin renderöijät. Avaimet: `:esikatselu` (tarkastelusivu) ja
  `:renderoi` (simulaattori, `(fn [solmu tila lapsi-fn])`). Uudelleenrekisteröinti korvaa edellisen,
  jotta tiedoston hot reload ei kaadu. Tupla-rekisteröinti on JVM-testin vastuulla."
  [id {:keys [esikatselu renderoi] :as renderoijat-kartta}]
  (when (and (nil? esikatselu) (nil? renderoi))
    (throw (js/Error. (str "Rekisteröinnissä ei ole renderöijää: " id))))
  (swap! renderoijat assoc id (select-keys renderoijat-kartta [:esikatselu :renderoi])))

(defn esikatselu [id]
  (get-in @renderoijat [id :esikatselu]))

(defn renderoi [id]
  (get-in @renderoijat [id :renderoi]))
