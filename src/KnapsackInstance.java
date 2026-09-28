public class KnapsackInstance {
    public int capacity;
    public int[] weights;
    public int[] values;
    public Integer knownOptimal; // can be null if the file doesn't have OPTIMAL

    public KnapsackInstance() {
    }
}