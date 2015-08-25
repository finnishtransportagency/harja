(ns harja.e2e.testutils)

(defn muokkaa-atomia [a newval]
  (reset! a newval)
  (reagent.core/flush))
