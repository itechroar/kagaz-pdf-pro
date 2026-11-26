import type { Meta, StoryObj } from "@storybook/react-vite";
import { DataRow } from "@editor/ui/DataRow";
import { ChipFlow } from "@editor/ui/ChipFlow";
import { Card } from "@editor/ui/Card";

const meta: Meta<typeof DataRow> = {
  title: "Primitives/DataRow",
  component: DataRow,
  tags: ["autodocs"],
  parameters: { layout: "padded" },
  decorators: [
    (S) => (
      <div style={{ maxWidth: "24rem" }}>
        <S />
      </div>
    ),
  ],
};
export default meta;
type Story = StoryObj<typeof DataRow>;

export const Single: Story = {
  args: { label: "Reviewer", children: "itechroar@users.noreply.github.com" },
};

export const Summary: Story = {
  render: () => (
    <Card padding="default">
      <div style={{ display: "flex", flexDirection: "column", gap: "0.5rem" }}>
        <DataRow label="Enforces" align="top">
          <ChipFlow items={["Classify", "Extract", "Name"]} />
        </DataRow>
        <DataRow label="Sources">3 selected</DataRow>
        <DataRow label="Reviewer">itechroar@users.noreply.github.com</DataRow>
      </div>
    </Card>
  ),
};
