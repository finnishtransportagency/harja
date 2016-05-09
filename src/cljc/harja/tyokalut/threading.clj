(ns harja.tyokalut.threading)

(defn- expand-operation [pos pred-sym value-sym [operation & operations]]
  (let [next-op-form (if (list? operation)
                       (if (= :first pos)
                         (concat (list (first operation)
                                       value-sym)
                                 (rest operation))
                         (concat operation
                                 (list value-sym)))
                       (list operation value-sym))]
    `(let [~value-sym ~next-op-form]
       (if-not (~pred-sym ~value-sym)
         ~value-sym
         ~(if (empty? operations)
            value-sym
            (expand-operation pos pred-sym value-sym operations))))))

(defmacro when->
  "Threads value through operations as the first argument (like ->). Stopping if the current value
  matches the predicate. Returns the first value that matches the predicate or the final value."
  [pred value & operations]
  (let [pred-sym (gensym "PRED")
        value-sym (gensym "VAL")]
    `(let [~pred-sym ~pred
           ~value-sym ~value]
       ~(expand-operation :first pred-sym value-sym operations))))

(defmacro when->>
  "Threads value through operations as the last argument (like ->>). Stopping if the current value
  matches the predicate. Returns the first value that matches the predicate or the final value."
  [pred value & operations]
  (let [pred-sym (gensym "PRED")
        value-sym (gensym "VAL")]
    `(let [~pred-sym ~pred
           ~value-sym ~value]
       ~(expand-operation :last pred-sym value-sym operations))))
